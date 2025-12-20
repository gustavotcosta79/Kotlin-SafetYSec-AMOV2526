package pt.isec.amov.safetysec.data.model

import java.util.Date

data class Alert (
    val id: String = "",
    val type: RuleType = RuleType.UNKNOWN,
    val userEmail : String = "", //apenas para display
    val protectedId: String = "", //sabermos quem causou o alerta

    //dados do evento
    val date: Date = Date(),
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,

    val solved: Boolean = false, //se o monitor já resolveu o alerta
    val cancelled: Boolean = false, //se o protegido decidiu cancelar o alerta nos 10s

    val videoUrl: String = ""

)