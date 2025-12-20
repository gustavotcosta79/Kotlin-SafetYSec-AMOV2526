package pt.isec.amov.safetysec.data.model

import com.google.firebase.firestore.PropertyName

class User (
     val id: String = "",
     val email: String = "",
     val name: String = "",

     @get:PropertyName("isMonitor")
     @set:PropertyName("isMonitor")
     var isMonitor: Boolean = false,

     @get:PropertyName("isProtected")
     @set:PropertyName("isProtected")
     var isProtected: Boolean = false,

    val cancellationCode: String = "1234",
    val connectionCode: String? = null, // ADICIONA ESTA LINHA
    val associatedMonitorIds: List <String> = emptyList(),
    val associatedProtegidoIds: List <String> = emptyList(),

    val lastLatitude: Double? = null,
    val lastLongitude: Double? = null,

     //para sabermos se o Protegido está "Offline" há muito tempo
    val lastLocationTime: Long? = null
)
