package com.law.app.util

/**
 * 通用结果封装
 */
sealed class Result<out T> {
    data class Success<out T>(val data: T) : Result<T>()
    data class Error(val message: String, val exception: Throwable? = null) : Result<Nothing>()
    data object Loading : Result<Nothing>()

    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this is Error
    val isLoading: Boolean get() = this is Loading

    fun getOrNull(): T? = (this as? Success)?.data

    fun getOrThrow(): T = when (this) {
        is Success -> data
        is Error -> throw exception ?: RuntimeException(message)
        is Loading -> throw IllegalStateException("Result is still loading")
    }

    inline fun <R> map(transform: (T) -> R): Result<R> = when (this) {
        is Success -> Success(transform(data))
        is Error -> this
        is Loading -> this
    }

    companion object {
        fun <T> success(data: T): Result<T> = Success(data)
        fun error(message: String, exception: Throwable? = null): Result<Nothing> =
            Error(message, exception)
        fun loading(): Result<Nothing> = Loading
    }
}
