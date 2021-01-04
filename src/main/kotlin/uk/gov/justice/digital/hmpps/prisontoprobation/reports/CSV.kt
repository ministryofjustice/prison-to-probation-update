package uk.gov.justice.digital.hmpps.prisontoprobation.reports

import com.opencsv.bean.StatefulBeanToCsvBuilder
import java.io.StringWriter

fun <T> Iterable<T>.asCSV(): String {
  val list = this
  val csv = StringWriter().apply {
    StatefulBeanToCsvBuilder<T>(this).build().write(list.iterator())
  }
  return csv.toString()
}
