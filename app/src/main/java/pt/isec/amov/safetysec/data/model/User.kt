package pt.isec.amov.safetysec.data.model

class User (
     val id: String = "",
     val email: String = "",
     val name: String = "",

     val isMonitor: Boolean = false,
     val isProtected: Boolean = false,

    val cancellationCode: String = "1234",
    val associatedMonitorIds: List <String> = emptyList(),
    val associatedProtegidoIds: List <String> = emptyList(),

    val lastLatitude: Double? = null,
    val lastLongitude: Double? = null,

     //para sabermos se o Protegido está "Offline" há muito tempo
    val lastLocationTime: Long? = null
)
