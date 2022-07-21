package cachet.plugins.health

import com.google.android.gms.fitness.data.DataType
import com.google.android.gms.fitness.data.Field
import com.google.android.gms.fitness.data.HealthDataTypes
import com.google.android.gms.fitness.data.HealthFields

enum class HealthDataType(
    val dataType: DataType,
    val field: Field,
) {
    ACTIVE_ENERGY_BURNED(DataType.TYPE_CALORIES_EXPENDED, Field.FIELD_CALORIES),
    BLOOD_GLUCOSE(HealthDataTypes.TYPE_BLOOD_GLUCOSE, HealthFields.FIELD_BLOOD_GLUCOSE_LEVEL),
    BLOOD_OXYGEN(HealthDataTypes.TYPE_OXYGEN_SATURATION, HealthFields.FIELD_OXYGEN_SATURATION),
    BLOOD_PRESSURE_DIASTOLIC(
        HealthDataTypes.TYPE_BLOOD_PRESSURE,
        HealthFields.FIELD_BLOOD_PRESSURE_DIASTOLIC
    ),
    BLOOD_PRESSURE_SYSTOLIC(
        HealthDataTypes.TYPE_BLOOD_PRESSURE,
        HealthFields.FIELD_BLOOD_PRESSURE_SYSTOLIC
    ),
    BODY_FAT_PERCENTAGE(DataType.TYPE_BODY_FAT_PERCENTAGE, Field.FIELD_PERCENTAGE),
    BODY_TEMPERATURE(HealthDataTypes.TYPE_BODY_TEMPERATURE, HealthFields.FIELD_BODY_TEMPERATURE),
    DISTANCE_DELTA(DataType.TYPE_DISTANCE_DELTA, Field.FIELD_DISTANCE),
    HEART_RATE(DataType.TYPE_HEART_RATE_BPM, Field.FIELD_BPM),
    HEIGHT(DataType.TYPE_HEIGHT, Field.FIELD_HEIGHT),
    MOVE_MINUTES(DataType.TYPE_MOVE_MINUTES, Field.FIELD_DURATION),
    SLEEP_ASLEEP(DataType.TYPE_SLEEP_SEGMENT, Field.FIELD_SLEEP_SEGMENT_TYPE),
    SLEEP_AWAKE(DataType.TYPE_SLEEP_SEGMENT, Field.FIELD_SLEEP_SEGMENT_TYPE),
    SLEEP_IN_BED(DataType.TYPE_SLEEP_SEGMENT, Field.FIELD_SLEEP_SEGMENT_TYPE),
    STEPS(DataType.TYPE_STEP_COUNT_DELTA, Field.FIELD_STEPS),
    WATER(DataType.TYPE_HYDRATION, Field.FIELD_VOLUME),
    WEIGHT(DataType.TYPE_WEIGHT, Field.FIELD_WEIGHT),
    WORKOUT(DataType.TYPE_ACTIVITY_SEGMENT, Field.FIELD_ACTIVITY);

    companion object {
        private val map = values().associateBy { it.name }

        fun fromString(s: String): HealthDataType =
            map[s] ?: throw IllegalArgumentException("Unsupported dataType: $s")
    }
}