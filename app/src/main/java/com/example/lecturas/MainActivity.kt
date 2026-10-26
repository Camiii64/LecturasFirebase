package com.example.lecturas

import android.os.Bundle
import android.text.InputType
import android.util.Patterns
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.Query
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.util.Locale

private val genres = listOf("Novela", "Ciencia ficción", "Historia", "Desarrollo personal")
private val statuses = listOf("Por leer", "Leyendo", "Terminado")

data class Book(
    var id: String = "",
    var title: String = "",
    var author: String = "",
    var genre: String = "Novela",
    var totalPages: Int = 0,
    var readPages: Int = 0,
    var status: String = "Por leer",
    var rating: Int? = null,
    var finishedAt: Long? = null
)

class MainActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference
    private val books = mutableListOf<Book>()
    private var goal = 0
    private var pagesReadThisYear = 0
    private var booksListener: ValueEventListener? = null
    private var booksQuery: Query? = null
    private var registerMode = false
    private val year: Int get() = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
    private val navy = 0xFF183B36.toInt()
    private val green = 0xFF287A60.toInt()
    private val cream = 0xFFF6F4EE.toInt()
    private val ink = 0xFF20302C.toInt()
    private val muted = 0xFF66736D.toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            val firebaseApp = FirebaseApp.initializeApp(applicationContext)
                ?: throw IllegalStateException("FirebaseApp.initializeApp devolvio null: faltan recursos de Google Services.")
            auth = FirebaseAuth.getInstance(firebaseApp)
            val firebaseDatabase = FirebaseDatabase.getInstance(firebaseApp)
            try {
                firebaseDatabase.setPersistenceEnabled(true)
            } catch (error: Exception) {
                android.util.Log.w("Lecturas", "No se pudo activar la cache offline; se continuara con Firebase en linea.", error)
            }
            database = firebaseDatabase.reference
        } catch (error: IllegalStateException) {
            showFirebaseSetup(error.localizedMessage)
            return
        }
        if (auth.currentUser == null) showAuth() else openUserData()
    }

    private fun showFirebaseSetup(detail: String?) {
        val page = page()
        page.addView(label("Lecturas", 34, navy, true))
        page.addView(label("No se pudo iniciar Firebase.", 19, ink, true))
        page.addView(label("Detalle tecnico: " + (detail ?: "Firebase lanzo una excepcion sin descripcion."), 15, muted))
        page.addView(label("La compilacion incluye la configuracion Firebase para com.example.lecturas. Comprueba que instalaste el APK mas reciente generado desde el proyecto LecturasFirebase. Si el mensaje continua, envia una captura de este detalle tecnico.", 15, ink))
        setContentView(scroll(page))
    }

    private fun openUserData() {
        val uid = auth.currentUser?.uid ?: return showAuth()
        val userRef = database.child("users").child(uid)
        userRef.child("goals").child(year.toString()).get().addOnSuccessListener {
            goal = (it.getValue(Long::class.java) ?: it.getValue(Int::class.java)?.toLong() ?: 0L).toInt()
            if (auth.currentUser != null) showHome()
        }
        userRef.child("annualPages").child(year.toString()).get().addOnSuccessListener { pagesReadThisYear = (it.getValue(Long::class.java) ?: 0L).toInt() }
        booksListener?.let { booksQuery?.removeEventListener(it) }
        booksQuery = userRef.child("books").orderByChild("title")
        booksListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                books.clear()
                snapshot.children.forEach { item -> item.getValue(Book::class.java)?.let { books.add(it.copy(id = item.key.orEmpty())) } }
                if (auth.currentUser != null) showHome()
            }
            override fun onCancelled(error: DatabaseError) { toast("No se pudo cargar la biblioteca: ${error.message}") }
        }
        booksQuery!!.addValueEventListener(booksListener!!)
    }

    private fun showAuth() {
        val root = page().apply { setPadding(dp(24), dp(28), dp(24), dp(28)) }
        val brand = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val mark = TextView(this).apply {
            text = "L"; textSize = 25f; setTextColor(0xFFFFFFFF.toInt()); gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            background = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(green) }
            layoutParams = LinearLayout.LayoutParams(dp(54), dp(54)).apply { marginEnd = dp(14) }
        }
        val brandWords = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        brandWords.addView(label("Lecturas", 28, navy, true))
        brandWords.addView(label("TU ESPACIO PARA LEER", 11, muted, true))
        brand.addView(mark); brand.addView(brandWords); root.addView(brand)
        root.addView(label(if (registerMode) "Empieza una nueva historia" else "Qué bueno verte", 23, ink, true))
        root.addView(label(if (registerMode) "Crea tu cuenta y guarda tu progreso." else "Entra para continuar con tus lecturas.", 15, muted))

        val formCard = MaterialCardView(this).apply {
            radius = dp(22).toFloat(); cardElevation = dp(2).toFloat(); strokeWidth = dp(1)
            strokeColor = 0xFFE5EAE6.toInt(); setCardBackgroundColor(0xFFFFFFFF.toInt()); layoutParams = marginParams()
        }
        val form = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(20), dp(20), dp(12)) }
        form.addView(label(if (registerMode) "Crear cuenta" else "Iniciar sesión", 20, navy, true))
        form.addView(label("Usa tu correo y una contraseña segura.", 14, muted))
        val emailField = inputField("Correo electrónico", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        val passwordField = inputField("Contraseña", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD, passwordToggle = true)
        val email = emailField.editText as TextInputEditText
        val password = passwordField.editText as TextInputEditText
        form.addView(emailField); form.addView(passwordField)
        var confirmationField: TextInputLayout? = null
        if (registerMode) {
            confirmationField = inputField("Confirmar contraseña", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD, passwordToggle = true)
            form.addView(confirmationField)
        }
        val confirmation = confirmationField?.editText as? TextInputEditText
        val feedback = TextView(this).apply {
            setTextColor(0xFFB3261E.toInt()); textSize = 13f; visibility = View.GONE
            setPadding(dp(4), dp(4), dp(4), dp(10))
        }
        form.addView(feedback)
        form.addView(button(if (registerMode) "Crear cuenta" else "Iniciar sesión") {
            emailField.error = null; passwordField.error = null; confirmationField?.error = null; feedback.visibility = View.GONE
            val mail = email.text?.toString()?.trim().orEmpty()
            val pass = password.text?.toString().orEmpty()
            if (!Patterns.EMAIL_ADDRESS.matcher(mail).matches()) { emailField.error = "Escribe un correo válido"; return@button }
            if (pass.length < 6) { passwordField.error = "Usa al menos 6 caracteres"; return@button }
            if (registerMode && pass != confirmation?.text?.toString().orEmpty()) { confirmationField?.error = "Las contraseñas no coinciden"; return@button }
            val task = if (registerMode) auth.createUserWithEmailAndPassword(mail, pass) else auth.signInWithEmailAndPassword(mail, pass)
            task.addOnCompleteListener { result ->
                if (result.isSuccessful) openUserData()
                else { feedback.text = authError(result.exception); feedback.visibility = View.VISIBLE }
            }
        })
        form.addView(button(if (registerMode) "Ya tengo cuenta · Iniciar sesión" else "Crear una cuenta", secondary = true) {
            registerMode = !registerMode; showAuth()
        })
        formCard.addView(form); root.addView(formCard)
        root.addView(label("Tu biblioteca se sincroniza con tu cuenta y queda disponible al volver a iniciar sesión.", 13, muted))
        setContentView(scroll(root))
    }

    private fun authError(error: Exception?): String {
        val message = error?.localizedMessage.orEmpty().uppercase(Locale.ROOT)
        return when {
            "CONFIGURATION_NOT_FOUND" in message -> "Firebase Authentication aún no tiene configurado el proveedor. En Firebase Console activa Authentication > Método de acceso > Correo electrónico/contraseña y guarda los cambios."
            "EMAIL_EXISTS" in message || "EMAIL_ALREADY_IN_USE" in message -> "Ese correo ya tiene una cuenta. Inicia sesión."
            "INVALID_LOGIN_CREDENTIALS" in message || "WRONG_PASSWORD" in message || "USER_NOT_FOUND" in message -> "El correo o la contraseña no coinciden."
            "WEAK_PASSWORD" in message -> "La contraseña debe tener al menos 6 caracteres."
            "INVALID_EMAIL" in message -> "El formato del correo no es válido."
            "TOO_MANY_ATTEMPTS_TRY_LATER" in message -> "Hubo muchos intentos. Espera un momento y vuelve a probar."
            else -> error?.localizedMessage ?: "No se pudo completar el acceso. Revisa tu conexión e inténtalo de nuevo."
        }
    }

    private fun showHome() {
        val user = auth.currentUser ?: return showAuth()
        val root = contentPage()
        header(root, "Hola, " + (user.email ?: "lector"))
        val finished = finishedThisYear().size
        val remaining = if (goal > 0) (goal - finished).coerceAtLeast(0) else 0
        val goalDetail = if (goal > 0) finished.toString() + " de " + goal + " libros terminados - faltan " + remaining else "Aun no has definido una meta para " + year
        root.addView(heroCard("Tu meta de lectura", goalDetail))
        root.addView(button(if (goal > 0) "Cambiar meta anual" else "Definir meta anual", secondary = true) { goalDialog() }, fullWidthActionParams())
        root.addView(label("Leyendo ahora", 21, navy, true))
        val current = books.filter { it.status == "Leyendo" }
        if (current.isEmpty()) root.addView(card("Sin lecturas en curso", "Cuando marques un libro como Leyendo, aparecera aqui."))
        current.forEach { root.addView(card(it.title + " - " + it.author, percent(it).toString() + "% completado  |  " + it.readPages + "/" + it.totalPages + " paginas")) }
        root.addView(label("Accesos rapidos", 19, navy, true))
        root.addView(buttonRow(
            button("+ Agregar libro") { bookDialog(null) },
            button("Mi biblioteca", secondary = true) { showLibrary() }
        ))
        root.addView(button("Ver estadisticas", secondary = true) { showStats() }, fullWidthActionParams())
        setContentView(scroll(root))
    }

    private fun showLibrary() {
        val root = contentPage()
        navHeader(root, "Mi biblioteca")
        root.addView(button("+ Agregar libro") { bookDialog(null) }, fullWidthActionParams())
        if (books.isEmpty()) root.addView(card("Tu biblioteca esta vacia", "Agrega el primer libro para empezar tu registro."))
        books.sortedBy { it.title.lowercase(Locale.getDefault()) }.forEach { book ->
            var detail = book.genre + "  |  " + book.status + "\n" + percent(book) + "% completado  |  " + book.readPages + "/" + book.totalPages + " paginas"
            if (book.status == "Terminado") detail += "\nCalificacion: " + (book.rating ?: "-") + "/5"
            root.addView(card(book.title + " - " + book.author, detail))
            root.addView(buttonRow(
                button("Editar") { bookDialog(book) },
                button("Eliminar", secondary = true) { confirmDelete(book) }
            ))
        }
        setContentView(scroll(root))
    }

    private fun showStats() {
        val root = contentPage()
        navHeader(root, "Estadisticas " + year)
        val finished = finishedThisYear()
        genres.forEach { genre -> root.addView(card(genre, "${finished.count { it.genre == genre }} libros terminados")) }
        val counts = genres.associateWith { genre -> finished.count { it.genre == genre } }
        val mostRead = counts.maxByOrNull { it.value }
        root.addView(card("Genero mas leido", if (finished.isEmpty() || mostRead?.value == 0) "Aun no hay libros terminados este ano" else "${mostRead?.key} (${mostRead?.value})"))
        root.addView(card("Paginas leidas este ano", pagesReadThisYear.toString()))
        val top = finished.filter { it.rating != null }.sortedWith(compareByDescending<Book> { it.rating }.thenBy { it.finishedAt ?: Long.MAX_VALUE }).firstOrNull()
        root.addView(card("Mejor calificado", top?.let { "${it.title} · ${it.rating}/5" } ?: "Todavia no hay calificaciones este ano"))
        setContentView(scroll(root))
    }

    private fun goalDialog() {
        val input = edit("Numero entero mayor que cero", InputType.TYPE_CLASS_NUMBER)
        if (goal > 0) input.setText(goal.toString())
        AlertDialog.Builder(this).setTitle("Meta anual $year").setView(input)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Guardar", null).create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val value = input.text.toString().toIntOrNull()
                        if (value == null || value <= 0) input.error = "La meta debe ser un entero mayor que cero"
                        else {
                            goal = value
                            auth.currentUser?.uid?.let { database.child("users").child(it).child("goals").child(year.toString()).setValue(value) }
                            dialog.dismiss(); showHome()
                        }
                    }
                }
                dialog.show()
            }
    }

    private fun bookDialog(existing: Book?) {
        val form = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(28, 4, 28, 0) }
        val title = edit("Titulo (2 a 60 caracteres)")
        val author = edit("Autor (2 a 60 caracteres)")
        val genre = spinner(genres)
        val total = edit("Total de paginas", InputType.TYPE_CLASS_NUMBER)
        val read = edit("Paginas leidas", InputType.TYPE_CLASS_NUMBER)
        val status = spinner(statuses)
        val rating = spinner(listOf("Sin calificacion", "1 estrella", "2 estrellas", "3 estrellas", "4 estrellas", "5 estrellas"))
        listOf(label("Titulo", 14, ink), title, label("Autor", 14, ink), author, label("Genero", 14, ink), genre, label("Total de paginas", 14, ink), total, label("Paginas leidas", 14, ink), read, label("Estado", 14, ink), status, label("Calificacion", 14, ink), rating).forEach(form::addView)
        existing?.let {
            title.setText(it.title); author.setText(it.author); total.setText(it.totalPages.toString()); read.setText(it.readPages.toString())
            genre.setSelection(genres.indexOf(it.genre).coerceAtLeast(0)); status.setSelection(statuses.indexOf(it.status).coerceAtLeast(0)); rating.setSelection(it.rating ?: 0)
        }
        val dialog = AlertDialog.Builder(this).setTitle(if (existing == null) "Nuevo libro" else "Editar libro")
            .setView(form).setNegativeButton("Cancelar", null).setPositiveButton("Guardar", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val t = title.text.toString().trim(); val a = author.text.toString().trim()
                val pages = total.text.toString().toIntOrNull(); val readPages = read.text.toString().toIntOrNull()
                val state = status.selectedItem.toString(); val stars = rating.selectedItemPosition.takeIf { it > 0 }
                when {
                    t.length !in 2..60 -> title.error = "El titulo es obligatorio y debe tener entre 2 y 60 caracteres"
                    a.length !in 2..60 -> author.error = "El autor es obligatorio y debe tener entre 2 y 60 caracteres"
                    pages == null || pages <= 0 -> total.error = "Ingresa un total entero mayor que cero"
                    readPages == null || readPages !in 0..pages -> read.error = "Debe estar entre 0 y el total de paginas"
                    state == "Terminado" && stars == null -> toast("Un libro terminado necesita una calificacion de 1 a 5")
                    else -> {
                        val now = System.currentTimeMillis()
                        val book = Book(
                            id = existing?.id.orEmpty(), title = t, author = a, genre = genre.selectedItem.toString(),
                            totalPages = pages, readPages = readPages, status = state, rating = if (state == "Terminado") stars else null,
                            finishedAt = if (state == "Terminado") existing?.finishedAt ?: now else null
                        )
                        saveBook(book); dialog.dismiss()
                    }
                }
            }
        }
        dialog.show()
    }

    private fun saveBook(book: Book) {
        val uid = auth.currentUser?.uid ?: return
        val userRef = database.child("users").child(uid)
        val ref = userRef.child("books")
        val key = book.id.ifBlank { ref.push().key ?: return toast("No se pudo crear el libro") }
        val oldPages = books.firstOrNull { it.id == key }?.readPages ?: 0
        val delta = book.readPages - oldPages
        val values = mapOf("title" to book.title, "author" to book.author, "genre" to book.genre,
            "totalPages" to book.totalPages, "readPages" to book.readPages, "status" to book.status,
            "rating" to book.rating, "finishedAt" to book.finishedAt)
        ref.child(key).setValue(values).addOnSuccessListener {
            if (delta != 0) userRef.child("annualPages").child(year.toString()).get().addOnSuccessListener { snap ->
                val next = ((snap.getValue(Long::class.java) ?: 0L) + delta).coerceAtLeast(0L)
                userRef.child("annualPages").child(year.toString()).setValue(next)
                pagesReadThisYear = next.toInt()
            }
            toast("Libro guardado")
        }.addOnFailureListener { toast("No se pudo guardar: ${it.localizedMessage}") }
    }

    private fun confirmDelete(book: Book) {
        AlertDialog.Builder(this).setTitle("Eliminar libro").setMessage("Eliminar ${book.title} de tu biblioteca?")
            .setNegativeButton("Cancelar", null).setPositiveButton("Eliminar") { _, _ ->
                auth.currentUser?.uid?.let { database.child("users").child(it).child("books").child(book.id).removeValue() }
            }.show()
    }

    private fun logout() {
        booksListener?.let { booksQuery?.removeEventListener(it) }
        booksListener = null; booksQuery = null; auth.signOut(); books.clear(); goal = 0; pagesReadThisYear = 0; showAuth()
    }

    private fun finishedThisYear(): List<Book> = books.filter { it.status == "Terminado" && it.finishedAt?.let { time -> java.util.Calendar.getInstance().apply { timeInMillis = time }.get(java.util.Calendar.YEAR) == year } == true }
    private fun percent(book: Book): Int = if (book.totalPages <= 0) 0 else (book.readPages * 100 / book.totalPages).coerceIn(0, 100)
    private fun spinner(options: List<String>) = Spinner(this).apply {
        adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, options)
        layoutParams = marginParams()
    }
    private fun inputField(hintText: String, type: Int, passwordToggle: Boolean = false) = TextInputLayout(this).apply {
        hint = hintText; boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
        setBoxCornerRadii(dp(14).toFloat(), dp(14).toFloat(), dp(14).toFloat(), dp(14).toFloat())
        boxStrokeColor = green
        endIconMode = if (passwordToggle) TextInputLayout.END_ICON_PASSWORD_TOGGLE else TextInputLayout.END_ICON_NONE
        layoutParams = marginParams()
        addView(TextInputEditText(this@MainActivity).apply {
            inputType = type; setSingleLine(true); textSize = 16f; setTextColor(ink); setPadding(dp(14), dp(14), dp(14), dp(14))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }
    private fun edit(hintText: String, type: Int = InputType.TYPE_CLASS_TEXT) = EditText(this).apply {
        hint = hintText; inputType = type; setSingleLine(true); textSize = 16f; setTextColor(ink)
        setPadding(dp(16), dp(13), dp(16), dp(13))
        background = android.graphics.drawable.GradientDrawable().apply {
            setColor(0xFFFFFFFF.toInt()); cornerRadius = dp(12).toFloat(); setStroke(dp(1), 0xFFD7E0DA.toInt())
        }
        layoutParams = marginParams()
    }
    private fun label(text: String, size: Int, color: Int, bold: Boolean = false) = TextView(this).apply {
        this.text = text; textSize = size.toFloat(); setTextColor(color); if (bold) setTypeface(null, android.graphics.Typeface.BOLD)
        setPadding(0, dp(7), 0, dp(7)); layoutParams = marginParams()
    }
    private fun button(text: String, secondary: Boolean = false, action: () -> Unit) = MaterialButton(this).apply {
        this.text = text; isAllCaps = false; textSize = 15f; setTextColor(if (secondary) navy else -1)
        backgroundTintList = android.content.res.ColorStateList.valueOf(if (secondary) 0xFFE5ECE7.toInt() else green)
        cornerRadius = dp(14); insetTop = 0; insetBottom = 0; minHeight = dp(50)
        elevation = if (secondary) 0f else dp(2).toFloat()
        setOnClickListener { action() }; layoutParams = marginParams()
    }
    private fun card(title: String, detail: String = "") = MaterialCardView(this).apply {
        radius = dp(18).toFloat(); cardElevation = dp(1).toFloat(); strokeWidth = dp(1)
        strokeColor = 0xFFE2E9E4.toInt(); setCardBackgroundColor(0xFFFFFFFF.toInt())
        layoutParams = marginParams().apply { bottomMargin = dp(12) }
        val content = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(12), dp(18), dp(12)) }
        content.addView(label(title, 17, navy, true)); if (detail.isNotEmpty()) content.addView(label(detail, 14, muted))
        addView(content)
    }
    private fun heroCard(title: String, detail: String) = MaterialCardView(this).apply {
        radius = dp(20).toFloat(); cardElevation = dp(2).toFloat(); setCardBackgroundColor(navy)
        layoutParams = marginParams().apply { bottomMargin = dp(16) }
        val content = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(17), dp(20), dp(15)) }
        content.addView(label(title, 17, 0xFFCDE7D9.toInt(), true)); content.addView(label(detail, 17, 0xFFFFFFFF.toInt(), true))
        addView(content)
    }
    private fun page() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(22, 26, 22, 24); setBackgroundColor(cream) }
    private fun contentPage() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(22), dp(20), dp(24))
        setBackgroundColor(cream)
    }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun scroll(content: View) = ScrollView(this).apply { addView(content); isFillViewport = true }
    private fun fullWidthActionParams() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)).apply {
        bottomMargin = dp(12)
    }
    private fun buttonRow(first: MaterialButton, second: MaterialButton) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        val firstParams = LinearLayout.LayoutParams(0, dp(50), 1f).apply { marginEnd = dp(6) }
        val secondParams = LinearLayout.LayoutParams(0, dp(50), 1f).apply { marginStart = dp(6) }
        addView(first, firstParams)
        addView(second, secondParams)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(14)
        }
    }
    private fun header(root: LinearLayout, title: String) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(label(title, 22, navy, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(button("Salir", true) { logout() }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(50)).apply { marginStart = dp(12) })
        row.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(16) }
        root.addView(row)
    }
    private fun navHeader(root: LinearLayout, title: String) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(button("Inicio", true) { showHome() }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(50)).apply { marginEnd = dp(12) })
        row.addView(label(title, 21, navy, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(16) }
        root.addView(row)
    }
    private fun marginParams() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 10 }
    private fun weightParams() = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = 6 }
    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()
}
