/*
 * Copyright 2020 Babylon Partners Limited
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.babylon.orbit2.coroutines

import com.babylon.orbit2.Operator
import com.babylon.orbit2.OrbitDslPlugin
import com.babylon.orbit2.VolatileContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Orbit plugin providing Kotlin coroutine DSL operators:
 *
 * * [transformSuspend]
 * * [transformFlow]
 */
object CoroutineDslPlugin : OrbitDslPlugin {

    @Suppress("UNCHECKED_CAST", "EXPERIMENTAL_API_USAGE")
    override fun <S : Any, E, SE : Any> apply(
        containerContext: OrbitDslPlugin.ContainerContext<S, SE>,
        flow: Flow<E>,
        operator: Operator<S, E>,
        createContext: (event: E) -> VolatileContext<S, E>
    ): Flow<Any?> {
        return when (operator) {
            is TransformSuspend<*, *, *> -> flow.map {
                if (operator.registerIdling) containerContext.settings.idlingRegistry.increment()

                @Suppress("UNCHECKED_CAST")
                with(operator as TransformSuspend<S, E, Any>) {
                    withContext(containerContext.backgroundDispatcher) {
                        createContext(it).block()
                    }
                }.also {
                    if (operator.registerIdling) containerContext.settings.idlingRegistry.decrement()
                }
            }
            is TransformFlow<*, *, *> -> flow.flatMapConcat {
                if (operator.registerIdling) containerContext.settings.idlingRegistry.increment()

                with(operator as TransformFlow<S, E, Any>) {
                    createContext(it).block().flowOn(containerContext.backgroundDispatcher)
                }.also {
                    if (operator.registerIdling) containerContext.settings.idlingRegistry.decrement()
                }
            }
            else -> flow
        }
    }
}
