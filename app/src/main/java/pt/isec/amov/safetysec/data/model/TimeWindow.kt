package pt.isec.amov.safetysec.data.model

data class TimeWindow (
    val id: String = "",
    val name: String = "",
    val startHour: Int = 9 , //por exemplo horario de trabalho
    val startMinute: Int = 0,
    val endHour: Int = 18,
    val endMinute: Int = 0,
    val activeDays: List <Boolean> = List(7){true}
){
    // Função auxiliar para ver se "agora" está dentro desta janela
    fun isActiveNow(): Boolean {
        val now = java.util.Calendar.getInstance()
        val currentDayIndex = now.get(java.util.Calendar.DAY_OF_WEEK) - 1 // 0 = Domingo, 1 = Segunda...

        // 1. Verifica o dia da semana
        if (!activeDays.getOrElse(currentDayIndex) { false }) return false

        // 2. Verifica as horas (converte tudo para minutos do dia para ser fácil comparar)
        val currentTotalMinutes = now.get(java.util.Calendar.HOUR_OF_DAY) * 60 + now.get(java.util.Calendar.MINUTE)
        val startTotalMinutes = startHour * 60 + startMinute
        val endTotalMinutes = endHour * 60 + endMinute

        return currentTotalMinutes in startTotalMinutes..endTotalMinutes
    }
}