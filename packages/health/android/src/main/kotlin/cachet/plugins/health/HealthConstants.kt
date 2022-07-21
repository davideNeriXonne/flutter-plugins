package cachet.plugins.health

import android.Manifest
import android.os.Build
import com.google.android.gms.fitness.FitnessActivities
import com.google.android.gms.fitness.data.DataType

object HealthConstants {
    const val CHANNEL_NAME = "flutter_health"
    const val MMOLL_2_MGDL = 18.0 // 1 mmoll= 18 mgdl

    private val SDK_INT = Build.VERSION.SDK_INT

    private val accessFineLocationPermission = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

    private val activityRecognitionPermission = if (SDK_INT >= Build.VERSION_CODES.Q) {
        arrayOf(Manifest.permission.ACTIVITY_RECOGNITION)
    } else {
        emptyArray()
    }

    private val bodySensorsPermission = if (SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        arrayOf(Manifest.permission.BODY_SENSORS)
    } else {
        emptyArray()
    }

    val dataTypePermissionsMap = hashMapOf(
        DataType.TYPE_ACTIVITY_SEGMENT to activityRecognitionPermission,
        DataType.TYPE_CALORIES_EXPENDED to activityRecognitionPermission,
        DataType.TYPE_DISTANCE_DELTA to activityRecognitionPermission + accessFineLocationPermission,
        DataType.TYPE_HEART_RATE_BPM to bodySensorsPermission,
        DataType.TYPE_LOCATION_SAMPLE to accessFineLocationPermission,
        DataType.TYPE_SPEED to accessFineLocationPermission,
        DataType.TYPE_STEP_COUNT_DELTA to activityRecognitionPermission,
    )

    val workoutTypeMap = hashMapOf(
        "AEROBICS" to FitnessActivities.AEROBICS,
        "AMERICAN_FOOTBALL" to FitnessActivities.FOOTBALL_AMERICAN,
        "ARCHERY" to FitnessActivities.ARCHERY,
        "AUSTRALIAN_FOOTBALL" to FitnessActivities.FOOTBALL_AUSTRALIAN,
        "BADMINTON" to FitnessActivities.BADMINTON,
        "BASEBALL" to FitnessActivities.BASEBALL,
        "BASKETBALL" to FitnessActivities.BASKETBALL,
        "BIATHLON" to FitnessActivities.BIATHLON,
        "BIKING" to FitnessActivities.BIKING,
        "BOXING" to FitnessActivities.BOXING,
        "CALISTHENICS" to FitnessActivities.CALISTHENICS,
        "CIRCUIT_TRAINING" to FitnessActivities.CIRCUIT_TRAINING,
        "CRICKET" to FitnessActivities.CRICKET,
        "CROSS_COUNTRY_SKIING" to FitnessActivities.SKIING_CROSS_COUNTRY,
        "CROSS_FIT" to FitnessActivities.CROSSFIT,
        "CURLING" to FitnessActivities.CURLING,
        "DANCING" to FitnessActivities.DANCING,
        "DIVING" to FitnessActivities.DIVING,
        "DOWNHILL_SKIING" to FitnessActivities.SKIING_DOWNHILL,
        "ELEVATOR" to FitnessActivities.ELEVATOR,
        "ELLIPTICAL" to FitnessActivities.ELLIPTICAL,
        "ERGOMETER" to FitnessActivities.ERGOMETER,
        "ESCALATOR" to FitnessActivities.ESCALATOR,
        "FENCING" to FitnessActivities.FENCING,
        "FRISBEE_DISC" to FitnessActivities.FRISBEE_DISC,
        "GARDENING" to FitnessActivities.GARDENING,
        "GOLF" to FitnessActivities.GOLF,
        "GUIDED_BREATHING" to FitnessActivities.GUIDED_BREATHING,
        "GYMNASTICS" to FitnessActivities.GYMNASTICS,
        "HANDBALL" to FitnessActivities.HANDBALL,
        "HIGH_INTENSITY_INTERVAL_TRAINING" to FitnessActivities.HIGH_INTENSITY_INTERVAL_TRAINING,
        "HIKING" to FitnessActivities.HIKING,
        "HOCKEY" to FitnessActivities.HOCKEY,
        "HORSEBACK_RIDING" to FitnessActivities.HORSEBACK_RIDING,
        "HOUSEWORK" to FitnessActivities.HOUSEWORK,
        "IN_VEHICLE" to FitnessActivities.IN_VEHICLE,
        "INTERVAL_TRAINING" to FitnessActivities.INTERVAL_TRAINING,
        "JUMP_ROPE" to FitnessActivities.JUMP_ROPE,
        "KAYAKING" to FitnessActivities.KAYAKING,
        "KETTLEBELL_TRAINING" to FitnessActivities.KETTLEBELL_TRAINING,
        "KICK_SCOOTER" to FitnessActivities.KICK_SCOOTER,
        "KICKBOXING" to FitnessActivities.KICKBOXING,
        "KITE_SURFING" to FitnessActivities.KITESURFING,
        "MARTIAL_ARTS" to FitnessActivities.MARTIAL_ARTS,
        "MEDITATION" to FitnessActivities.MEDITATION,
        "MIXED_MARTIAL_ARTS" to FitnessActivities.MIXED_MARTIAL_ARTS,
        "P90X" to FitnessActivities.P90X,
        "PARAGLIDING" to FitnessActivities.PARAGLIDING,
        "PILATES" to FitnessActivities.PILATES,
        "POLO" to FitnessActivities.POLO,
        "RACQUETBALL" to FitnessActivities.RACQUETBALL,
        "ROCK_CLIMBING" to FitnessActivities.ROCK_CLIMBING,
        "ROWING" to FitnessActivities.ROWING,
        "RUGBY" to FitnessActivities.RUGBY,
        "RUNNING_JOGGING" to FitnessActivities.RUNNING_JOGGING,
        "RUNNING_SAND" to FitnessActivities.RUNNING_SAND,
        "RUNNING_TREADMILL" to FitnessActivities.RUNNING_TREADMILL,
        "RUNNING" to FitnessActivities.RUNNING,
        "SAILING" to FitnessActivities.SAILING,
        "SCUBA_DIVING" to FitnessActivities.SCUBA_DIVING,
        "SKATING_CROSS" to FitnessActivities.SKATING_CROSS,
        "SKATING_INDOOR" to FitnessActivities.SKATING_INDOOR,
        "SKATING_INLINE" to FitnessActivities.SKATING_INLINE,
        "SKATING" to FitnessActivities.SKATING,
        "SKIING_BACK_COUNTRY" to FitnessActivities.SKIING_BACK_COUNTRY,
        "SKIING_KITE" to FitnessActivities.SKIING_KITE,
        "SKIING_ROLLER" to FitnessActivities.SKIING_ROLLER,
        "SLEDDING" to FitnessActivities.SLEDDING,
        "SNOWBOARDING" to FitnessActivities.SNOWBOARDING,
        "SOCCER" to FitnessActivities.FOOTBALL_SOCCER,
        "SOFTBALL" to FitnessActivities.SOFTBALL,
        "SQUASH" to FitnessActivities.SQUASH,
        "STAIR_CLIMBING_MACHINE" to FitnessActivities.STAIR_CLIMBING_MACHINE,
        "STAIR_CLIMBING" to FitnessActivities.STAIR_CLIMBING,
        "STANDUP_PADDLEBOARDING" to FitnessActivities.STANDUP_PADDLEBOARDING,
        "STILL" to FitnessActivities.STILL,
        "STRENGTH_TRAINING" to FitnessActivities.STRENGTH_TRAINING,
        "SURFING" to FitnessActivities.SURFING,
        "SWIMMING_OPEN_WATER" to FitnessActivities.SWIMMING_OPEN_WATER,
        "SWIMMING_POOL" to FitnessActivities.SWIMMING_POOL,
        "SWIMMING" to FitnessActivities.SWIMMING,
        "TABLE_TENNIS" to FitnessActivities.TABLE_TENNIS,
        "TEAM_SPORTS" to FitnessActivities.TEAM_SPORTS,
        "TENNIS" to FitnessActivities.TENNIS,
        "TILTING" to FitnessActivities.TILTING,
        "VOLLEYBALL_BEACH" to FitnessActivities.VOLLEYBALL_BEACH,
        "VOLLEYBALL_INDOOR" to FitnessActivities.VOLLEYBALL_INDOOR,
        "VOLLEYBALL" to FitnessActivities.VOLLEYBALL,
        "WAKEBOARDING" to FitnessActivities.WAKEBOARDING,
        "WALKING_FITNESS" to FitnessActivities.WALKING_FITNESS,
        "WALKING_NORDIC" to FitnessActivities.WALKING_NORDIC,
        "WALKING_STROLLER" to FitnessActivities.WALKING_STROLLER,
        "WALKING_TREADMILL" to FitnessActivities.WALKING_TREADMILL,
        "WALKING" to FitnessActivities.WALKING,
        "WATER_POLO" to FitnessActivities.WATER_POLO,
        "WEIGHTLIFTING" to FitnessActivities.WEIGHTLIFTING,
        "WHEELCHAIR" to FitnessActivities.WHEELCHAIR,
        "WINDSURFING" to FitnessActivities.WINDSURFING,
        "YOGA" to FitnessActivities.YOGA,
        "ZUMBA" to FitnessActivities.ZUMBA,
        "OTHER" to FitnessActivities.OTHER,
    )
}