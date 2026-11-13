fun parseWeight(rawText: String): String? {
    // أولًا: إذا تعرّف OCR على الفاصلة أو النقطة
    val formatted = Regex("""\d{2}[.,]\d{2}""")
        .find(rawText)
        ?.value
        ?.replace(',', '.')

    if (formatted != null) return formatted

    // بعض الشاشات تجعل OCR يتجاهل النقطة العشرية
    val digits = rawText.filter(Char::isDigit)
    return if (digits.length == 4) {
        "${digits.substring(0, 2)}.${digits.substring(2)}"
    } else {
        null // لا نعرض قراءة غير مؤكدة
    }
}