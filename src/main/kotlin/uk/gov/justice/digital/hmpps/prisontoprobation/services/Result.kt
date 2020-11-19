package uk.gov.justice.digital.hmpps.prisontoprobation.services

data class TelemetryEvent(val name: String, val attributes: Map<String, String?> = mapOf())

sealed class Result<out T, out E> {
  data class Success<out T>(val value: T) : Result<T, Nothing>()
  data class Ignore<out E>(val reason: E) : Result<Nothing, E>()

  // either return the value or execute the block that must return "nothing" e.g it must throw or return out of parent
}

inline fun <T, E> Result<T, E>.onIgnore(block: (Result.Ignore<E>) -> Nothing): T {
  return when (this) {
    is Result.Success<T> -> value
    is Result.Ignore<E> -> block(this)
  }
}
