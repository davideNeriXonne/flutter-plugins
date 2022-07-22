package cachet.plugins.health

import android.content.Context
import com.google.android.gms.fitness.data.*
import java.util.concurrent.TimeUnit

object HealthUtil {
    fun buildDataSource(
        context: Context,
        dataType: DataType,
        streamName: String? = null
    ) = DataSource.Builder()
        .setDataType(dataType)
        .setType(DataSource.TYPE_RAW)
        .setDevice(Device.getLocalDevice(context))
        .setAppPackageName(context)
        .apply { if (streamName != null) setStreamName(streamName) }
        .build()

    fun buildDataSet(
        context: Context,
        dataType: DataType,
        startTime: Long,
        endTime: Long,
        dataPointBuilderBlock: DataPoint.Builder.() -> Unit,
        streamName: String? = null
    ): DataSet {
        val dataSource = DataSource.Builder()
            .setDataType(dataType)
            .setType(DataSource.TYPE_RAW)
            .setDevice(Device.getLocalDevice(context))
            .setAppPackageName(context)
            .apply { if (streamName != null) setStreamName(streamName) }
            .build()

        val dataPoint = DataPoint.builder(dataSource).run {
            if (startTime == endTime) {
                setTimestamp(startTime, TimeUnit.MILLISECONDS)
            } else {
                setTimeInterval(startTime, endTime, TimeUnit.MILLISECONDS)
            }
            dataPointBuilderBlock(this)
            build()
        }

        return DataSet.builder(dataSource)
            .add(dataPoint)
            .build()
    }
}