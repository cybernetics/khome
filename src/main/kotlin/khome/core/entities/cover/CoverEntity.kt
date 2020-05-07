package khome.core.entities.cover

import khome.core.entities.EntitySubject

@ExperimentalStdlibApi
abstract class CoverEntity(name: String) : EntitySubject<String>("cover", name) {
    open val isOpen get() = state.state == "open"
    open val isClosed get() = state.state == "closed"
}
