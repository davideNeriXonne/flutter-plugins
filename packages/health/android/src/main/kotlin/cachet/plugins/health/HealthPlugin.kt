package cachet.plugins.health

import android.app.Activity
import android.content.Intent
import androidx.annotation.NonNull
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.fitness.Fitness
import com.google.android.gms.fitness.FitnessActivities
import com.google.android.gms.fitness.FitnessOptions
import com.google.android.gms.fitness.data.*
import com.google.android.gms.fitness.request.DataReadRequest
import com.google.android.gms.fitness.request.SessionInsertRequest
import com.google.android.gms.fitness.request.SessionReadRequest
import com.google.android.gms.fitness.result.DataReadResponse
import com.google.android.gms.fitness.result.SessionReadResponse
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import io.flutter.Log
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.PluginRegistry
import java.util.*
import java.util.concurrent.*


class HealthPlugin : FlutterPlugin, MethodChannel.MethodCallHandler, ActivityAware, PluginRegistry.ActivityResultListener {

  companion object {
    private const val TAG = "HealthPlugin"
    private const val RC_GOOGLE_FIT = 1889
  }

  private lateinit var channel: MethodChannel
  private lateinit var threadPoolExecutor: ExecutorService

  private var attachedActivity: Activity? = null
  private var permissionResult: MethodChannel.Result? = null

  override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    threadPoolExecutor = Executors.newFixedThreadPool(4)
    channel = MethodChannel(flutterPluginBinding.binaryMessenger, HealthConstants.CHANNEL_NAME)
    channel.setMethodCallHandler(this)
  }

  override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
    channel.setMethodCallHandler(null)
    threadPoolExecutor.shutdown()
  }

  override fun onAttachedToActivity(binding: ActivityPluginBinding) {
    attachedActivity = binding.activity
    binding.addActivityResultListener(this)
  }

  override fun onDetachedFromActivityForConfigChanges() {
    onDetachedFromActivity()
  }

  override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
    onAttachedToActivity(binding)
  }

  override fun onDetachedFromActivity() {
    attachedActivity = null
  }

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
    if (requestCode == RC_GOOGLE_FIT) {
      permissionResult?.success(resultCode == Activity.RESULT_OK)
    }
    permissionResult = null
    return false
  }

  override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
    Log.d(TAG, "onMethodCall: method = '${call.method}', arguments = '${call.arguments}'")
    try {
      when (call.method) {
        "hasPermissions" -> hasPermissions(call, result)
        "requestAuthorization" -> requestAuthorization(call, result)
        "getData" -> getData(call, result)
        "getTotalStepsInInterval" -> getTotalStepsInInterval(call, result)
        "writeData" -> writeData(call, result)
        "writeWorkoutData" -> writeWorkoutData(call, result)
        else -> result.notImplemented()
      }
    } catch (e: Throwable) {
      Log.e(TAG, call.method, e)
      result.error(call.method, e.message ?: e.javaClass.simpleName, null)
    }
  }

  private fun callToFitnessOptions(call: MethodCall): FitnessOptions {
    val types = call.argument<List<String>>("types")!!.map(HealthDataType::fromString)
    val permissions = call.argument<List<Int>>("permissions")!!
    if (types.size != permissions.size) {
      throw IllegalArgumentException("types and permissions must have the same size")
    }

    val optionsBuilder = FitnessOptions.builder()
    types.forEachIndexed { i, type ->
      val access = permissions[i]
      when (access) {
        0 -> optionsBuilder.addDataType(type.dataType, FitnessOptions.ACCESS_READ)
        1 -> optionsBuilder.addDataType(type.dataType, FitnessOptions.ACCESS_WRITE)
        2 -> {
          optionsBuilder.addDataType(type.dataType, FitnessOptions.ACCESS_READ)
          optionsBuilder.addDataType(type.dataType, FitnessOptions.ACCESS_WRITE)
        }
        else -> throw IllegalArgumentException("Unknown access type $access")
      }
      if (type == HealthDataType.SLEEP_ASLEEP || type == HealthDataType.SLEEP_AWAKE || type == HealthDataType.SLEEP_IN_BED || type == HealthDataType.WORKOUT) {
        optionsBuilder.accessSleepSessions(FitnessOptions.ACCESS_READ)
        when (access) {
          0 -> optionsBuilder.accessSleepSessions(FitnessOptions.ACCESS_READ)
          1 -> optionsBuilder.accessSleepSessions(FitnessOptions.ACCESS_WRITE)
          2 -> {
            optionsBuilder.accessSleepSessions(FitnessOptions.ACCESS_READ)
            optionsBuilder.accessSleepSessions(FitnessOptions.ACCESS_WRITE)
          }
          else -> throw IllegalArgumentException("Unknown access type $access")
        }
      }
    }
    return optionsBuilder.build()
  }

  private fun hasPermissions(call: MethodCall, result: MethodChannel.Result) {
    val activity = attachedActivity ?: return result.success(false)
    result.success(GoogleSignIn.hasPermissions(
      GoogleSignIn.getLastSignedInAccount(activity),
      callToFitnessOptions(call)
    ))
  }

  private fun requestAuthorization(call: MethodCall, result: MethodChannel.Result) {
    val activity = attachedActivity ?: return result.success(false)

    val account = GoogleSignIn.getLastSignedInAccount(activity)
    val fitnessOptions = callToFitnessOptions(call)
    if (GoogleSignIn.hasPermissions(account, fitnessOptions)) return result.success(true)

    permissionResult = result
    GoogleSignIn.requestPermissions(activity, RC_GOOGLE_FIT, account, fitnessOptions)
  }

  private fun isIntField(dataSource: DataSource, unit: Field): Boolean {
    val dataPoint = DataPoint.builder(dataSource).build()
    val value = dataPoint.getValue(unit)
    return value.format == Field.FORMAT_INT32
  }

  /// Extracts the (numeric) value from a Health Data Point
  private fun getHealthDataValue(dataPoint: DataPoint, field: Field): Any {
    val value = dataPoint.getValue(field)
    // Conversion is needed because glucose is stored as mmoll in Google Fit;
    // while mgdl is used for glucose in this plugin.
    val isGlucose = field == HealthFields.FIELD_BLOOD_GLUCOSE_LEVEL
    return when (value.format) {
      Field.FORMAT_FLOAT -> if (!isGlucose) value.asFloat() else value.asFloat() * HealthConstants.MMOLL_2_MGDL
      Field.FORMAT_INT32 -> value.asInt()
      Field.FORMAT_STRING -> value.asString()
      else -> Log.e("Unsupported format:", value.format.toString())
    }
  }

  private fun writeData(call: MethodCall, result: MethodChannel.Result) {
    val appContext = attachedActivity?.applicationContext ?: return result.success(false)

    val type = HealthDataType.fromString(call.argument<String>("dataTypeKey")!!)
    val startTime = call.argument<Long>("startTime")!!
    val endTime = call.argument<Long>("endTime")!!
    val value = call.argument<Float>("value")!!

    val fitnessOptionsBuilder = FitnessOptions.builder()
    fitnessOptionsBuilder.addDataType(type.dataType, FitnessOptions.ACCESS_WRITE)
    if (type.dataType == DataType.TYPE_SLEEP_SEGMENT) {
      fitnessOptionsBuilder.accessSleepSessions(FitnessOptions.ACCESS_READ)
    }
    val fitnessOptions = fitnessOptionsBuilder.build()

    val dataSource = HealthUtil.buildDataSource(appContext, type.dataType)
    val dataPointBuilder = DataPoint.builder(dataSource)

    // Set time
    if (startTime == endTime) {
      dataPointBuilder.setTimestamp(startTime, TimeUnit.MILLISECONDS)
    } else {
      dataPointBuilder.setTimeInterval(startTime, endTime, TimeUnit.MILLISECONDS)
    }

    // Set value
    if (isIntField(dataSource, type.field)) {
      dataPointBuilder.setField(type.field, value.toInt())
    } else if (type.field == HealthFields.FIELD_BLOOD_GLUCOSE_LEVEL) {
      // Conversion needed because glucose is stored as mmoll in Google Fit, while mgdl is used in this plugin.
      dataPointBuilder.setField(type.field, (value / HealthConstants.MMOLL_2_MGDL).toFloat())
    } else {
      dataPointBuilder.setField(type.field, value)
    }

    try {
      val account = GoogleSignIn.getAccountForExtension(appContext, fitnessOptions)
      Fitness.getHistoryClient(appContext, account)
        .insertData(DataSet.builder(dataSource).add(dataPointBuilder.build()).build())
        .addOnSuccessListener {
          Log.i("FLUTTER_HEALTH::SUCCESS", "DataSet added successfully!")
          result.success(true)
        }
        .addOnFailureListener { e ->
          Log.w("FLUTTER_HEALTH::ERROR", "There was an error adding the DataSet", e)
          result.success(false)
        }
    } catch (e: Exception) {
      result.success(false)
    }
  }

  private fun writeWorkoutData(call: MethodCall, result: MethodChannel.Result) {
    val activity = attachedActivity ?: return result.success(false)

    val type = call.argument<String>("activityType")!!
    val startTime = call.argument<Long>("startTime")!!
    val endTime = call.argument<Long>("endTime")!!
    val totalEnergyBurned = call.argument<Int>("totalEnergyBurned")
    val totalDistance = call.argument<Int>("totalDistance")

    val activityType = HealthConstants.workoutTypeMap[type] ?: FitnessActivities.UNKNOWN

    // Setup builders
    val fitnessOptionsBuilder = FitnessOptions.builder()
    val sessionInsertRequestBuilder = SessionInsertRequest.Builder().setSession(
      Session.Builder()
        .setName(activityType) // TODO: Make a sensible name / allow user to set name
        .setDescription("")
        .setIdentifier(UUID.randomUUID().toString())
        .setActivity(activityType)
        .setStartTime(startTime, TimeUnit.MILLISECONDS)
        .setEndTime(endTime, TimeUnit.MILLISECONDS)
        .build()
    )

    // Add activity
    fitnessOptionsBuilder.addDataType(DataType.TYPE_ACTIVITY_SEGMENT, FitnessOptions.ACCESS_WRITE)
    sessionInsertRequestBuilder.addDataSet(HealthUtil.buildDataSet(
      activity,
      DataType.TYPE_ACTIVITY_SEGMENT,
      startTime, endTime,
      { setActivityField(Field.FIELD_ACTIVITY, activityType) },
      "FLUTTER_HEALTH - Activity"
    ))

    // Add distance if provided
    if (totalDistance != null) {
      fitnessOptionsBuilder.addDataType(DataType.TYPE_DISTANCE_DELTA, FitnessOptions.ACCESS_WRITE)
      sessionInsertRequestBuilder.addDataSet(HealthUtil.buildDataSet(
        activity,
        DataType.TYPE_DISTANCE_DELTA,
        startTime, endTime,
        { setField(Field.FIELD_DISTANCE, totalDistance.toFloat()) },
        "FLUTTER_HEALTH - Distance"
      ))
    }

    // Add energyBurned if provided
    if (totalEnergyBurned != null) {
      fitnessOptionsBuilder.addDataType(DataType.TYPE_CALORIES_EXPENDED, FitnessOptions.ACCESS_WRITE)
      sessionInsertRequestBuilder.addDataSet(HealthUtil.buildDataSet(
        activity,
        DataType.TYPE_CALORIES_EXPENDED,
        startTime, endTime,
        { setField(Field.FIELD_CALORIES, totalEnergyBurned.toFloat()) },
        "FLUTTER_HEALTH - Calories"
      ))
    }

    try {
      Fitness
        .getSessionsClient(
          activity.applicationContext,
          GoogleSignIn.getAccountForExtension(activity.applicationContext, fitnessOptionsBuilder.build())
        )
        .insertSession(sessionInsertRequestBuilder.build())
        .addOnSuccessListener {
          Log.i("FLUTTER_HEALTH::SUCCESS", "Workout was successfully added!")
          result.success(true)
        }
        .addOnFailureListener { e ->
          Log.w("FLUTTER_HEALTH::ERROR", "There was a problem adding the workout: ", e)
          result.success(false)
        }
    } catch (e: Exception) {
      result.success(false)
    }
  }


  private fun getData(call: MethodCall, result: MethodChannel.Result) {
    val activity = attachedActivity ?: return result.success(null)

    // Get call arguments
    val type = HealthDataType.fromString(call.argument<String>("dataTypeKey")!!)
    val startTime = call.argument<Long>("startTime")!!
    val endTime = call.argument<Long>("endTime")!!

    // Build FitnessOptions, adding special cases for accessing workouts or sleep data.
    val fitnessOptionsBuilder = FitnessOptions.builder()
    fitnessOptionsBuilder.addDataType(type.dataType)
    if (type.dataType == DataType.TYPE_SLEEP_SEGMENT) {
      fitnessOptionsBuilder.accessSleepSessions(FitnessOptions.ACCESS_READ)
    } else if (type.dataType == DataType.TYPE_ACTIVITY_SEGMENT) {
      fitnessOptionsBuilder.accessActivitySessions(FitnessOptions.ACCESS_READ)
      fitnessOptionsBuilder.addDataType(DataType.TYPE_CALORIES_EXPENDED, FitnessOptions.ACCESS_READ)
      fitnessOptionsBuilder.addDataType(DataType.TYPE_DISTANCE_DELTA, FitnessOptions.ACCESS_READ)
    }
    val fitnessOptions = fitnessOptionsBuilder.build()

    val account = GoogleSignIn.getAccountForExtension(activity.applicationContext, fitnessOptions)

    // Handle data types
    when (type.dataType) {
      DataType.TYPE_SLEEP_SEGMENT -> {
        Fitness.getSessionsClient(activity.applicationContext, account)
          .readSession(
            SessionReadRequest.Builder()
              .setTimeInterval(startTime, endTime, TimeUnit.MILLISECONDS)
              .enableServerQueries()
              .readSessionsFromAllApps()
              .includeSleepSessions()
              .build()
          )
          .addOnSuccessListener(threadPoolExecutor, sleepDataHandler(type, result))
          .addOnFailureListener(errHandler(result))
      }
      DataType.TYPE_ACTIVITY_SEGMENT -> {
        Fitness.getSessionsClient(activity.applicationContext, account)
          .readSession(
            SessionReadRequest.Builder()
              .setTimeInterval(startTime, endTime, TimeUnit.MILLISECONDS)
              .enableServerQueries()
              .readSessionsFromAllApps()
              .includeActivitySessions()
              .readIfGranted(activity.applicationContext, type.dataType)
              .readIfGranted(activity.applicationContext, DataType.TYPE_CALORIES_EXPENDED)
              .readIfGranted(activity.applicationContext, DataType.TYPE_DISTANCE_DELTA)
              .build()
          )
          .addOnSuccessListener(threadPoolExecutor, workoutDataHandler(type, result))
          .addOnFailureListener(errHandler(result))
      }
      else -> {
        Fitness.getHistoryClient(activity.applicationContext, account)
          .readData(
            DataReadRequest.Builder()
              .readIfGranted(activity.applicationContext, type.dataType)
              .setTimeRange(startTime, endTime, TimeUnit.MILLISECONDS)
              .build()
          )
          .addOnSuccessListener(threadPoolExecutor, dataHandler(type, result))
          .addOnFailureListener(errHandler(result))
      }
    }
  }

  private fun dataHandler(type: HealthDataType, result: MethodChannel.Result) =
    OnSuccessListener { response: DataReadResponse ->
      /// Fetch all data points for the specified DataType
      val dataSet = response.getDataSet(type.dataType)
      /// For each data point, extract the contents and send them to Flutter, along with date and unit.
      val healthData = dataSet.dataPoints.map {
        val source = it.originalDataSource
        hashMapOf(
          "value" to getHealthDataValue(it, type.field),
          "date_from" to it.getStartTime(TimeUnit.MILLISECONDS),
          "date_to" to it.getEndTime(TimeUnit.MILLISECONDS),
          "source_name" to (source.appPackageName ?: source.device?.model ?: ""),
          "source_id" to source.streamIdentifier
        )
      }
      attachedActivity?.runOnUiThread { result.success(healthData) }
    }

  private fun errHandler(result: MethodChannel.Result) = OnFailureListener { exception ->
    attachedActivity?.runOnUiThread { result.success(null) }
    Log.i("FLUTTER_HEALTH::ERROR", exception.message ?: "unknown error")
    Log.i("FLUTTER_HEALTH::ERROR", exception.stackTrace.toString())
  }

  private fun sleepDataHandler(type: HealthDataType, result: MethodChannel.Result) =
    OnSuccessListener { response: SessionReadResponse ->
      val healthData: MutableList<Map<String, Any?>> = mutableListOf()
      for (session in response.sessions) {

        // Return sleep time in Minutes if requested ASLEEP data
        if (type == HealthDataType.SLEEP_ASLEEP) {
          healthData.add(
            hashMapOf(
              "value" to session.getEndTime(TimeUnit.MINUTES) - session.getStartTime(TimeUnit.MINUTES),
              "date_from" to session.getStartTime(TimeUnit.MILLISECONDS),
              "date_to" to session.getEndTime(TimeUnit.MILLISECONDS),
              "unit" to "MINUTES",
              "source_name" to session.appPackageName,
              "source_id" to session.identifier
            )
          )
        }

        if (type == HealthDataType.SLEEP_IN_BED) {
          val dataSets = response.getDataSet(session)

          // If the sleep session has finer granularity sub-components, extract them:
          if (dataSets.isNotEmpty()) {
            for (dataSet in dataSets) {
              for (dataPoint in dataSet.dataPoints) {
                // searching OUT OF BED data
                if (dataPoint.getValue(Field.FIELD_SLEEP_SEGMENT_TYPE).asInt() != 3) {
                  val source = dataPoint.originalDataSource
                  healthData.add(
                    hashMapOf(
                      "value" to dataPoint.getEndTime(TimeUnit.MINUTES) - dataPoint.getStartTime(TimeUnit.MINUTES),
                      "date_from" to dataPoint.getStartTime(TimeUnit.MILLISECONDS),
                      "date_to" to dataPoint.getEndTime(TimeUnit.MILLISECONDS),
                      "unit" to "MINUTES",
                      "source_name" to (source.appPackageName ?: source.device?.model ?: "unknown"),
                      "source_id" to source.streamIdentifier
                    )
                  )
                }
              }
            }
          } else {
            healthData.add(
              hashMapOf(
                "value" to session.getEndTime(TimeUnit.MINUTES) - session.getStartTime(TimeUnit.MINUTES),
                "date_from" to session.getStartTime(TimeUnit.MILLISECONDS),
                "date_to" to session.getEndTime(TimeUnit.MILLISECONDS),
                "unit" to "MINUTES",
                "source_name" to session.appPackageName,
                "source_id" to session.identifier
              )
            )
          }
        }

        if (type == HealthDataType.SLEEP_AWAKE) {
          val dataSets = response.getDataSet(session)
          for (dataSet in dataSets) {
            for (dataPoint in dataSet.dataPoints) {
              // searching SLEEP AWAKE data
              if (dataPoint.getValue(Field.FIELD_SLEEP_SEGMENT_TYPE).asInt() == 1) {
                val source = dataPoint.originalDataSource
                healthData.add(
                  hashMapOf(
                    "value" to dataPoint.getEndTime(TimeUnit.MINUTES) - dataPoint.getStartTime(TimeUnit.MINUTES),
                    "date_from" to dataPoint.getStartTime(TimeUnit.MILLISECONDS),
                    "date_to" to dataPoint.getEndTime(TimeUnit.MILLISECONDS),
                    "unit" to "MINUTES",
                    "source_name" to (source.appPackageName ?: source.device?.model ?: "unknown"),
                    "source_id" to source.streamIdentifier
                  )
                )
              }
            }
          }
        }
      }
      attachedActivity?.runOnUiThread { result.success(healthData) }
    }

  private fun workoutDataHandler(type: HealthDataType, result: MethodChannel.Result) =
    OnSuccessListener { response: SessionReadResponse ->
      val healthData: MutableList<Map<String, Any?>> = mutableListOf()
      val workoutTypeEntries = HealthConstants.workoutTypeMap.entries
      for (session in response.sessions) {
        // Look for calories and distance if they
        var totalEnergyBurned = 0.0
        var totalDistance = 0.0
        for (dataSet in response.getDataSet(session)) {
          if (dataSet.dataType == DataType.TYPE_CALORIES_EXPENDED) {
            for (dataPoint in dataSet.dataPoints) {
              totalEnergyBurned += dataPoint.getValue(Field.FIELD_CALORIES).toString().toDouble()
            }
          }
          if (dataSet.dataType == DataType.TYPE_DISTANCE_DELTA) {
            for (dataPoint in dataSet.dataPoints) {
              totalDistance += dataPoint.getValue(Field.FIELD_DISTANCE).toString().toDouble()
            }
          }
        }
        healthData.add(
          hashMapOf(
            "workoutActivityType" to workoutTypeEntries.first { it.value == session.activity }.key,
            "totalEnergyBurned" to if (totalEnergyBurned == 0.0) null else totalEnergyBurned,
            "totalEnergyBurnedUnit" to "KILOCALORIE",
            "totalDistance" to if (totalDistance == 0.0) null else totalDistance,
            "totalDistanceUnit" to "METER",
            "date_from" to session.getStartTime(TimeUnit.MILLISECONDS),
            "date_to" to session.getEndTime(TimeUnit.MILLISECONDS),
            "unit" to "MINUTES",
            "source_name" to session.appPackageName,
            "source_id" to session.identifier
          )
        )
      }
      attachedActivity?.runOnUiThread { result.success(healthData) }
    }

  private fun getTotalStepsInInterval(call: MethodCall, result: MethodChannel.Result) {
    val start = call.argument<Long>("startTime")!!
    val end = call.argument<Long>("endTime")!!

    val activity = attachedActivity ?: return

    val stepsDataType = keyToHealthDataType(STEPS)
    val aggregatedDataType = keyToHealthDataType(AGGREGATE_STEP_COUNT)

    val fitnessOptions = FitnessOptions.builder()
      .addDataType(stepsDataType)
      .addDataType(aggregatedDataType)
      .build()
    val gsa = GoogleSignIn.getAccountForExtension(activity, fitnessOptions)

    val ds = DataSource.Builder()
      .setAppPackageName("com.google.android.gms")
      .setDataType(stepsDataType)
      .setType(DataSource.TYPE_DERIVED)
      .setStreamName("estimated_steps")
      .build()

    val duration = (end - start).toInt()

    val request = DataReadRequest.Builder()
      .aggregate(ds)
      .bucketByTime(duration, TimeUnit.MILLISECONDS)
      .setTimeRange(start, end, TimeUnit.MILLISECONDS)
      .build()

    Fitness.getHistoryClient(activity, gsa).readData(request)
      .addOnFailureListener(errHandler(result))
      .addOnSuccessListener(threadPoolExecutor, getStepsInRange(start, end, aggregatedDataType, result))
  }

  private fun getStepsInRange(
    start: Long,
    end: Long,
    aggregatedDataType: DataType,
    result: MethodChannel.Result
  ) =
    OnSuccessListener { response: DataReadResponse ->

      val map = HashMap<Long, Int>() // need to return to Dart so can't use sparse array
      for (bucket in response.buckets) {
        val dp = bucket.dataSets.firstOrNull()?.dataPoints?.firstOrNull()
        if (dp != null) {
          print(dp)

          val count = dp.getValue(aggregatedDataType.fields[0])

          val startTime = dp.getStartTime(TimeUnit.MILLISECONDS)
          val startDate = Date(startTime)
          val endDate = Date(dp.getEndTime(TimeUnit.MILLISECONDS))
          Log.i("FLUTTER_HEALTH::SUCCESS", "returning $count steps for $startDate - $endDate")
          map[startTime] = count.asInt()
        } else {
          val startDay = Date(start)
          val endDay = Date(end)
          Log.i("FLUTTER_HEALTH::ERROR", "no steps for $startDay - $endDay")
        }
      }

      assert(map.size <= 1) { "getTotalStepsInInterval should return only one interval. Found: ${map.size}" }
      attachedActivity?.runOnUiThread { result.success(map.values.firstOrNull()) }
    }
}
