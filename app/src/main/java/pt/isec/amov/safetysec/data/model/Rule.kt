package pt.isec.amov.safetysec.data.model

import android.accessibilityservice.GestureDescription

class Rule (
    val id: String = "",
    val type: RuleType = RuleType.UNKNOWN,
    val description: String = "",
    val isActive: Boolean = false, //segundo o enunciado o protegido pode revogar as regras

    val valueDouble: Double ? = null, // usamos este campo para armazenar a velocidade ou a inatividade
    val latitude: Double? = null,
    val longitude: Double? =null,
    val radius: Double? = null,
    
    val monitorId: String = "", // para sabermos que monitor criou a regra
    val protectedId: String = "" // para sabermos a que protegido se aplica

)

