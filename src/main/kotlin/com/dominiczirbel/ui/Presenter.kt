package com.dominiczirbel.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import com.dominiczirbel.ui.Presenter.StateOrError.State
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * A presenter abstraction which controls the state of a particular piece of the UI.
 *
 * The presenter continually listens for [events] and processes them via [reactTo], which calls [mutateState] to update
 * the view [state].
 */
abstract class Presenter<State, Event> constructor(
    /**
     * The [CoroutineScope] under which this presenter operates, typically bound to the UI's point in the composition
     * (i.e. from [androidx.compose.runtime.rememberCoroutineScope]).
     */
    protected val scope: CoroutineScope,

    /**
     * The initial [State] of the content.
     */
    initialState: State,

    /**
     * An optional key by which the event flow collection is remembered; the same event flow will be used as long as
     * this value stays the same but will be recreated when the key changes. This is necessary because calls to [state]
     * will be at the same point in the composition even if the presenter object changes, and so the flow may not be
     * reset by default.
     */
    private val key: Any? = null,

    /**
     * The strategy by which to handle concurrent events, i.e. events whose processing (via [reactTo]) has not completed
     * before the next event is emitted.
     */
    private val eventMergeStrategy: EventMergeStrategy = EventMergeStrategy.MERGE,

    /**
     * The strategy by which to handle errors in the event stream, i.e. exceptions thrown by [reactTo].
     */
    private val errorStrategy: ErrorStrategy = ErrorStrategy.THROW,

    /**
     * An optional list of events which should be emitted at the beginning of the event flow, e.g. to load content.
     */
    private val startingEvents: List<Event>? = null
) {
    /**
     * Determines how concurrent events are merged, i.e. when a new event is emitted before the previous event was fully
     * processed.
     */
    enum class EventMergeStrategy {
        /**
         * Only the latest event is processed; processing of previous events is cancelled when a new event arrives.
         */
        LATEST,

        /**
         * All events are processed concurrently.
         */
        MERGE
    }

    /**
     * Determines how errors in the event flow are handled.
     */
    enum class ErrorStrategy {
        /**
         * Exceptions thrown in the event flow (e.g. by [reactTo]) are re-thrown by [state].
         */
        THROW,

        /**
         * Exceptions thrown in the event flow (e.g. by [reactTo]) are ignored and the last successful state is returned
         * by [state].
         */
        IGNORE
    }

    /**
     * Gets the current [State] of the presenter, possibly throwing an exception depending on [errorStrategy].
     *
     * Only returns a meaningful value if [open] has been called (and is still running) and is generally only used for
     * testing; most usages should use the [state] composable function instead.
     */
    val state: State
        get() = stateFlow.value.getState(errorStrategy)

    /**
     * The last non-error state; will never throw an exception.
     */
    private val lastState: State
        get() = stateFlow.value.getState(ErrorStrategy.IGNORE)

    /**
     * A [MutableStateFlow] which exposes the current state (via the [StateOrError] wrapper, possibly wrapping an
     * exception instead). Should only be modified internally, and writes must be synchronized.
     *
     * Ideally we might use a simpler mechanism to represent the state, but e.g. [androidx.compose.runtime.MutableState]
     * does not allow writes during a snapshot and so cannot support arbitrary concurrency.
     */
    private val stateFlow = MutableStateFlow<StateOrError<State>>(State(initialState))

    private val events = MutableSharedFlow<Event>()

    private val logTag by lazy { this::class.simpleName }

    /**
     * The core event [Flow], which includes emitting [startingEvents], reacting to events according to the
     * [eventMergeStrategy] with [reactTo], and catching errors.
     *
     * This must be its own function to allow restarting the event flow on error by making a recursive call to [flow].
     */
    private fun flow(startingEvents: List<Event>? = this.startingEvents): Flow<Event> {
        return events
            .onStart { startingEvents?.forEach { emit(it) } }
            .onEach { log("Event -> $it") }
            .let { flow ->
                when (eventMergeStrategy) {
                    EventMergeStrategy.LATEST -> flow.flatMapLatest { flow<Event> { reactTo(it) } }
                    EventMergeStrategy.MERGE -> flow.flatMapMerge { flow { reactTo(it) } }
                }
            }
            .catch { throwable ->
                log("Error -> $throwable")

                synchronized(this) {
                    stateFlow.value = StateOrError.Error(lastState = lastState, throwable = throwable)
                }

                emitAll(flow(null))
            }
    }

    /**
     * Opens this presenter, indefinitely waiting for events in the event flow and handling them.
     *
     * This function never returns.
     *
     * Typically only used from tests, most usages should call [state] instead, which opens the presenter and collects
     * its state as a composition-aware state.
     */
    suspend fun open() {
        flow().collect()
    }

    /**
     * Listens and handles events for this presenter and returns its current state. This function is appropriate to be
     * called in a composition and returns a composition-aware state.
     */
    @Composable
    fun state(context: CoroutineContext = EmptyCoroutineContext): State {
        remember(key) {
            scope.launch(context = context) {
                open()
            }
        }

        return stateFlow.collectAsState(context = context).value.getState(errorStrategy)
    }

    /**
     * Emits the given [event], possibly suspending until there is enough buffer space in the event flow. Most cases
     * should use [emitAsync] instead.
     */
    suspend fun emit(event: Event) {
        events.emit(event)
    }

    /**
     * Emits the given [event] on a new coroutine spawned from this presenter's [scope], and returns immediately.
     */
    fun emitAsync(event: Event, context: CoroutineContext = EmptyCoroutineContext) {
        scope.launch(context = context) { emit(event) }
    }

    /**
     * Mutates the current [state] of the view according to [transform].
     *
     * The last non-error state will be passed as the parameter of [transform], which should return an updated state (or
     * null to not update the state).
     *
     * This method is thread-safe and may be called concurrently, but must block to allow only a single concurrent
     * writer of the state.
     */
    protected fun mutateState(transform: (State) -> State?) {
        synchronized(this) {
            transform(lastState)?.let { transformed ->
                log("State -> $transformed")
                stateFlow.value = State(transformed)
            }
        }
    }

    /**
     * Handles the given [event], typically by mutating the current state via [mutateState] after making remote calls,
     * etc. May throw exceptions, which will be handled according to the [errorStrategy].
     */
    abstract suspend fun reactTo(event: Event)

    /**
     * Logs a [message] to the console, open to allow no-op logging in tests.
     */
    protected open fun log(message: String) {
        println("[$logTag] $message")
    }

    /**
     * Represents a state of the view or an error.
     */
    private sealed class StateOrError<State> {
        /**
         * Gets the state according to the given [errorStrategy]. May throw an exception rather than returning a [State]
         * depending on the [errorStrategy].
         */
        abstract fun getState(errorStrategy: ErrorStrategy): State

        /**
         * Represents a successful view [state].
         */
        data class State<State>(val state: State) : StateOrError<State>() {
            override fun getState(errorStrategy: ErrorStrategy) = state
        }

        /**
         * Represents an error case, with both the last non-error successful state [lastState] and the [throwable] that
         * that caused the current error.
         */
        data class Error<State>(val lastState: State, val throwable: Throwable) : StateOrError<State>() {
            override fun getState(errorStrategy: ErrorStrategy): State {
                return when (errorStrategy) {
                    ErrorStrategy.THROW -> throw throwable
                    ErrorStrategy.IGNORE -> lastState
                }
            }
        }
    }
}
