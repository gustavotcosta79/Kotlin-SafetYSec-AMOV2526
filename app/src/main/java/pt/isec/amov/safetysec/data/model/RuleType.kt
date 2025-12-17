package pt.isec.amov.safetysec.data.model

enum class RuleType {
    UNKNOWN,
    QUEDA,                 // Sem parâmetros
    ACIDENTE,              // Sem parâmetros
    GEOFENCING,            // Usa lat, long, radius
    CONTROLO_VELOCIDADE,   // Usa valueDouble (km/h)
    INATIVIDADE,           // Usa valueDouble (minutos)
    BOTAO_PANICO           // Sem parâmetros
}