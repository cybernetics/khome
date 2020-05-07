package khome

import io.ktor.util.KtorExperimentalAPI
import khome.calling.EntityBasedServiceCall
import khome.calling.ServiceCall
import khome.calling.ServiceCallMutator
import khome.calling.ServiceDataInterface
import khome.core.ErrorResponseListener
import khome.core.MessageInterface
import khome.core.ResultResponse
import khome.core.dependencyInjection.KhomeKoinComponent
import khome.core.entities.EntityCollection
import khome.core.entities.EntitySubject
import khome.core.events.EntityObserverExceptionHandler
import khome.core.events.EventData
import khome.core.events.HassEvent
import khome.core.mapping.ObjectMapper
import khome.observing.ObservableCoroutine
import khome.observing.EntityObservableContext
import khome.observing.EntityObservableFunction
import khome.observing.HassEventListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ObsoleteCoroutinesApi
import mu.KotlinLogging
import org.koin.core.get
import java.util.UUID

@ExperimentalStdlibApi
@Suppress("unused")
@ObsoleteCoroutinesApi
@KtorExperimentalAPI
abstract class KhomeComponent : KhomeKoinComponent {
    open val logger = KotlinLogging.logger { }

    val hassApi: HassApi = get()
    private val objectMapper = get<ObjectMapper>()
    fun ServiceDataInterface.toJson(): String = objectMapper.toJson(this)
    fun MessageInterface.toJson(): String = objectMapper.toJson(this)

    /**
     * A function to build an [ServiceCall] object, which is the base
     * to all [home-assistant](https://www.home-assistant.io/) websocket service api calls.
     * The ServiceCaller object is then serialized and send to homeassistant.
     * [Home-Assistant Websocket-Api](https://developers.home-assistant.io/docs/en/external_api_websocket.html).
     *
     * @see ServiceCall
     */
    @ObsoleteCoroutinesApi
    @KtorExperimentalAPI
    suspend inline fun <reified CallType : ServiceCall> callService(noinline mutate: ServiceCallMutator<CallType>? = null) {
        val service: CallType = get()
        if (mutate != null) service.apply(mutate)
        hassApi.callHassService(service)
    }

    suspend inline fun <reified Event : HassEvent> emitEvent(eventData: EventData? = null) =
        hassApi.emitHassEvent(get<Event>(), eventData)

    suspend inline fun <reified CallType : EntityBasedServiceCall> Iterable<EntitySubject<*>>.callServiceEach(noinline mutate: CallType.(EntitySubject<*>) -> Unit) =
        forEach { callService<CallType> { mutate(it) } }
}
@ExperimentalStdlibApi
abstract class Repository<Entity : EntitySubject<*>> : KhomeKoinComponent {
    abstract val entity: Entity

    @PublishedApi
    internal val hassApi = get<HassApi>()

    suspend inline fun <reified CallType : EntityBasedServiceCall> callService(noinline mutate: ServiceCallMutator<CallType>? = null) {
        val service = get<CallType>()
        if (mutate != null) service.apply(mutate)
        service.serviceData.entityId = entity
        hassApi.callHassService(service)
    }
}
@ExperimentalStdlibApi
abstract class CollectionRepository<EntityType : EntitySubject<*>> : KhomeKoinComponent {
    abstract val entities: EntityCollection<EntityType>

    @PublishedApi
    internal val hassApi = get<HassApi>()

    suspend inline fun <reified CallType : EntityBasedServiceCall> callServiceEach(noinline mutate: ServiceCallMutator<CallType>? = null) =
        entities.forEach { entity ->
            val service = get<CallType>()
            if (mutate != null) service.apply(mutate)
            service.serviceData.entityId = entity
            hassApi.callHassService(service)
        }
}
@ExperimentalStdlibApi
abstract class EntityObserver<Entity : EntitySubject<*>> : KhomeKoinComponent {
    private val coroutineContext = Dispatchers.IO
    private val exceptionHandler: EntityObserverExceptionHandler = get()
    abstract val observedEntity: Entity

    fun onStateChange(observable: EntityObservableFunction): Entity {
        val handle: String = UUID.randomUUID().toString()
        val observableContext = coroutineContext + exceptionHandler + EntityObservableContext(observedEntity, handle)
        observedEntity[handle] = ObservableCoroutine(observableContext, observable)
        return observedEntity
    }
}
@ExperimentalStdlibApi
abstract class ErrorResponseObserver : KhomeKoinComponent {
    fun onErrorResponse(callback: suspend CoroutineScope.(ResultResponse) -> Unit) =
        ErrorResponseListener(
            context = Dispatchers.IO,
            errorResponseEvent = get(),
            exceptionHandler = get(),
            listener = callback
        ).lifeCycleHandler
}

abstract class HassEventObserver<EventType : HassEvent> : KhomeKoinComponent {

    abstract val observedHassEvent: EventType

    fun onHassEvent(callback: suspend CoroutineScope.(EventData) -> Unit) =
        HassEventListener(
            context = Dispatchers.IO,
            hassEvent = observedHassEvent,
            exceptionHandler = get(),
            listener = callback
        ).lifeCycleHandler
}
