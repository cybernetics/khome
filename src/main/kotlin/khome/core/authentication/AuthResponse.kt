package khome.core.authentication

import khome.core.MessageInterface

internal data class AuthResponse(
    val type: String,
    val message: String?,
    val haVersion: String
) : MessageInterface
