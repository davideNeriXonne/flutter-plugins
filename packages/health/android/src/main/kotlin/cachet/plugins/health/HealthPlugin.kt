package cachet.plugins.health

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.util.Log
import androidx.annotation.NonNull
import androidx.core.content.ContextCompat
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
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry.ActivityResultListener
import io.flutter.plugin.common.PluginRegistry.Registrar
import java.util.*
import java.util.concurrent.*


class HealthPlugin(private var channel: MethodChannel? = null) : MethodCallHandler,
  ActivityResultListener, Result, ActivityAware, FlutterPlugin {
  private var result: Result? = null
  private var handler: Handler? = null
  private var activity: Activity? = null
  private var threadPoolExecutor: ExecutorService? = null

  override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    channel = MethodChannel(flutterPluginBinding.binaryMessenger, HealthConstants.CHANNEL_NAME)
    channel?.setMethodCallHandler(this)
    threadPoolExecutor = Executors.newFixedThreadPool(4)
  }

  override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
    channel = null
    activity = null
    threadPoolExecutor?.shutdown()
    threadPoolExecutor = null
  }

  // This static function is optional and equivalent to onAttachedToEngine. It supports the old
  // pre-Flutter-1.12 Android projects. You are encouraged to continue supporting
  // plugin registration via this function while apps migrate to use the new Android APIs
  // post-flutter-1.12 via https://flutter.dev/go/android-project-migration.
  //
  // It is encouraged to share logic between onAttachedToEngine and registerWith to keep
  // them functionally equivalent. Only one of onAttachedToEngine or registerWith will be called
  // depending on the user's project. onAttachedToEngine or registerWith must both be defined
  // in the same class.
  companion object {
    @Suppress("unused")
    @JvmStatic
    fun registerWith(registrar: Registrar) {
      val channel = MethodChannel(registrar.messenger(), HealthConstants.CHANNEL_NAME)
      val plugin = HealthPlugin(channel)
      registrar.addActivityResultListener(plugin)
      channel.setMethodCallHandler(plugin)
    }
  }

  /// DataTypes to register
  // private val fitnessOptions = FitnessOptions.builder()
  //         .addDataType(keyToHealthDataType(BODY_FAT_PERCENTAGE), FitnessOptions.ACCESS_READ)
  //         .addDataType(keyToHealthDataType(HEIGHT), FitnessOptions.ACCESS_READ)
  //         .addDataType(keyToHealthDataType(WEIGHT), FitnessOptions.ACCESS_READ)
  //         .addDataType(keyToHealthDataType(STEPS), FitnessOptions.ACCESS_READ)
  //         .addDataType(keyToHealthDataType(ACTIVE_ENERGY_BURNED), FitnessOptions.ACCESS_READ)
  //         .addDataType(keyToHealthDataType(HEART_RATE), FitnessOptions.ACCESS_READ)
  //         .addDataType(keyToHealthDataType(BODY_TEMPERATURE), FitnessOptions.ACCESS_READ)
  //         .addDataType(keyToHealthDataType(BLOOD_PRESSURE_SYSTOLIC), FitnessOptions.ACCESS_READ)
  //         .addDataType(keyToHealthDataType(BLOOD_OXYGEN), FitnessOptions.ACCESS_READ)
  //         .addDataType(keyToHealthDataType(BLOOD_GLUCOSE), FitnessOptions.ACCESS_READ)
  //         .addDataType(keyToHealthDataType(MOVE_MINUTES), FitnessOptions.ACCESS_READ)
  //         .addDataType(keyToHealthDataType(DISTANCE_DELTA), FitnessOptions.ACCESS_READ)
  //         .addDataType(keyToHealthDataType(WATER), FitnessOptions.ACCESS_READ)
  //         .addDataType(keyToHealthDataType(SLEEP_ASLEEP), FitnessOptions.ACCESS_READ)
  //         .accessActivitySessions(FitnessOptions.ACCESS_READ)
  //         .accessSleepSessions(FitnessOptions.ACCESS_READ)
  //         .build()


  override fun success(p0: Any?) {
    handler?.post { result?.success(p0) }
  }

  override fun notImplemented() {
    handler?.post { result?.notImplemented() }
  }

  override fun error(
    errorCode: String, errorMessage: String?, errorDetails: Any?
  ) {
    handler?.post { result?.error(errorCode, errorMessage, errorDetails) }
  }


  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
    if (requestCode == HealthConstants.GOOGLE_FIT_PERMISSIONS_REQUEST_CODE) {
      if (resultCode == Activity.RESULT_OK) {
        Log.d("FLUTTER_HEALTH", "Access Granted!")
        mResult?.success(true)
      } else if (resultCode == Activity.RESULT_CANCELED) {
        Log.d("FLUTTER_HEALTH", "Access Denied!")
        mResult?.success(false)
      }
    }
    return false
  }

  private var mResult: Result? = null

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

  private fun writeData(call: MethodCall, result: Result) {

    if (activity == null) {
      result.success(false)
      return
    }

    val type = HealthDataType.fromString(call.argument<String>("dataTypeKey")!!)
    val startTime = call.argument<Long>("startTime")!!
    val endTime = call.argument<Long>("endTime")!!
    val value = call.argument<Float>("value")!!

    val typesBuilder = FitnessOptions.builder()
    typesBuilder.addDataType(type.dataType, FitnessOptions.ACCESS_WRITE)

    val dataSource = DataSource.Builder()
      .setDataType(type.dataType)
      .setType(DataSource.TYPE_RAW)
      .setDevice(Device.getLocalDevice(activity!!.applicationContext))
      .setAppPackageName(activity!!.applicationContext)
      .build()

    val builder = if (startTime == endTime)
      DataPoint.builder(dataSource)
        .setTimestamp(startTime, TimeUnit.MILLISECONDS)
    else
      DataPoint.builder(dataSource)
        .setTimeInterval(startTime, endTime, TimeUnit.MILLISECONDS)

    // Conversion is needed because glucose is stored as mmoll in Google Fit;
    // while mgdl is used for glucose in this plugin.
    val isGlucose = type.field == HealthFields.FIELD_BLOOD_GLUCOSE_LEVEL
    val dataPoint = if (!isIntField(dataSource, type.field))
      builder.setField(type.field, (if (!isGlucose) value else (value / HealthConstants.MMOLL_2_MGDL).toFloat()))
        .build() else
      builder.setField(type.field, value.toInt()).build()

    val dataSet = DataSet.builder(dataSource)
      .add(dataPoint)
      .build()

    if (type.dataType == DataType.TYPE_SLEEP_SEGMENT) {
      typesBuilder.accessSleepSessions(FitnessOptions.ACCESS_READ)
    }
    val fitnessOptions = typesBuilder.build()
    try {
      val googleSignInAccount =
        GoogleSignIn.getAccountForExtension(activity!!.applicationContext, fitnessOptions)
      Fitness.getHistoryClient(activity!!.applicationContext, googleSignInAccount)
        .insertData(dataSet)
        .addOnSuccessListener {
          Log.i("FLUTTER_HEALTH::SUCCESS", "DataSet added successfully!")
          result.success(true)
        }
        .addOnFailureListener { e ->
          Log.w("FLUTTER_HEALTH::ERROR", "There was an error adding the DataSet", e)
          result.success(false)
        }
    } catch (e3: Exception) {
      result.success(false)
    }
  }

  private fun writeWorkoutData(call: MethodCall, result: Result) {
    if (activity == null) {
      result.success(false)
      return
    }

    val type = call.argument<String>("activityType")!!
    val startTime = call.argument<Long>("startTime")!!
    val endTime = call.argument<Long>("endTime")!!
    val totalEnergyBurned = call.argument<Int>("totalEnergyBurned")
    val totalDistance = call.argument<Int>("totalDistance")

    val activityType = HealthConstants.workoutTypeMap[type] ?: FitnessActivities.UNKNOWN

    // Create the Activity Segment DataSource
    val activitySegmentDataSource = DataSource.Builder()
      .setAppPackageName(activity!!.packageName)
      .setDataType(DataType.TYPE_ACTIVITY_SEGMENT)
      .setStreamName("FLUTTER_HEALTH - Activity")
      .setType(DataSource.TYPE_RAW)
      .build()
    // Create the Activity Segment
    val activityDataPoint = DataPoint.builder(activitySegmentDataSource)
      .setTimeInterval(startTime, endTime, TimeUnit.MILLISECONDS)
      .setActivityField(Field.FIELD_ACTIVITY, activityType)
      .build()
    // Add DataPoint to DataSet
    val activitySegments = DataSet.builder(activitySegmentDataSource)
      .add(activityDataPoint)
      .build()

    // If distance is provided
    var distanceDataSet: DataSet? = null
    if (totalDistance != null) {
      // Create a data source
      val distanceDataSource = DataSource.Builder()
        .setAppPackageName(activity!!.packageName)
        .setDataType(DataType.TYPE_DISTANCE_DELTA)
        .setStreamName("FLUTTER_HEALTH - Distance")
        .setType(DataSource.TYPE_RAW)
        .build()

      val distanceDataPoint = DataPoint.builder(distanceDataSource)
        .setTimeInterval(startTime, endTime, TimeUnit.MILLISECONDS)
        .setField(Field.FIELD_DISTANCE, totalDistance.toFloat())
        .build()
      // Create a data set
      distanceDataSet = DataSet.builder(distanceDataSource)
        .add(distanceDataPoint)
        .build()
    }
    // If energyBurned is provided
    var energyDataSet: DataSet? = null
    if (totalEnergyBurned != null) {
      // Create a data source
      val energyDataSource = DataSource.Builder()
        .setAppPackageName(activity!!.packageName)
        .setDataType(DataType.TYPE_CALORIES_EXPENDED)
        .setStreamName("FLUTTER_HEALTH - Calories")
        .setType(DataSource.TYPE_RAW)
        .build()

      val energyDataPoint = DataPoint.builder(energyDataSource)
        .setTimeInterval(startTime, endTime, TimeUnit.MILLISECONDS)
        .setField(Field.FIELD_CALORIES, totalEnergyBurned.toFloat())
        .build()
      // Create a data set
      energyDataSet = DataSet.builder(energyDataSource)
        .add(energyDataPoint)
        .build()
    }

    // Finish session setup
    val session = Session.Builder()
      .setName(activityType) // TODO: Make a sensible name / allow user to set name
      .setDescription("")
      .setIdentifier(UUID.randomUUID().toString())
      .setActivity(activityType)
      .setStartTime(startTime, TimeUnit.MILLISECONDS)
      .setEndTime(endTime, TimeUnit.MILLISECONDS)
      .build()
    // Build a session and add the values provided
    val sessionInsertRequestBuilder = SessionInsertRequest.Builder()
      .setSession(session)
      .addDataSet(activitySegments)
    if (totalDistance != null) {
      sessionInsertRequestBuilder.addDataSet(distanceDataSet!!)
    }
    if (totalEnergyBurned != null) {
      sessionInsertRequestBuilder.addDataSet(energyDataSet!!)
    }
    val insertRequest = sessionInsertRequestBuilder.build()

    val fitnessOptionsBuilder = FitnessOptions.builder()
      .addDataType(DataType.TYPE_ACTIVITY_SEGMENT, FitnessOptions.ACCESS_WRITE)
    if (totalDistance != null) {
      fitnessOptionsBuilder.addDataType(DataType.TYPE_DISTANCE_DELTA, FitnessOptions.ACCESS_WRITE)
    }
    if (totalEnergyBurned != null) {
      fitnessOptionsBuilder.addDataType(DataType.TYPE_CALORIES_EXPENDED, FitnessOptions.ACCESS_WRITE)
    }
    val fitnessOptions = fitnessOptionsBuilder.build()

    try {
      val googleSignInAccount =
        GoogleSignIn.getAccountForExtension(activity!!.applicationContext, fitnessOptions)
      if (!GoogleSignIn.hasPermissions(googleSignInAccount, fitnessOptions)) {
        GoogleSignIn.requestPermissions(
          activity!!,
          HealthConstants.GOOGLE_FIT_PERMISSIONS_REQUEST_CODE,
          googleSignInAccount,
          fitnessOptions
        )
      }
      Fitness.getSessionsClient(
        activity!!.applicationContext,
        GoogleSignIn.getAccountForExtension(activity!!.applicationContext, fitnessOptions)
      )
        .insertSession(insertRequest)
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


  private fun getData(call: MethodCall, result: Result) {
    if (activity == null) {
      result.success(null)
      return
    }

    val type = HealthDataType.fromString(call.argument<String>("dataTypeKey")!!)
    val startTime = call.argument<Long>("startTime")!!
    val endTime = call.argument<Long>("endTime")!!

    val typesBuilder = FitnessOptions.builder()
    typesBuilder.addDataType(type.dataType)

    // Add special cases for accessing workouts or sleep data.
    if (type.dataType == DataType.TYPE_SLEEP_SEGMENT) {
      typesBuilder.accessSleepSessions(FitnessOptions.ACCESS_READ)
    } else if (type.dataType == DataType.TYPE_ACTIVITY_SEGMENT) {
      typesBuilder.accessActivitySessions(FitnessOptions.ACCESS_READ)
        .addDataType(DataType.TYPE_CALORIES_EXPENDED, FitnessOptions.ACCESS_READ)
        .addDataType(DataType.TYPE_DISTANCE_DELTA, FitnessOptions.ACCESS_READ)
    }
    val fitnessOptions = typesBuilder.build()

    val googleSignInAccount =
      GoogleSignIn.getAccountForExtension(activity!!.applicationContext, fitnessOptions)
    // Handle data types
    when (type.dataType) {
      DataType.TYPE_SLEEP_SEGMENT -> {
        // request to the sessions for sleep data
        val request = SessionReadRequest.Builder()
          .setTimeInterval(startTime, endTime, TimeUnit.MILLISECONDS)
          .enableServerQueries()
          .readSessionsFromAllApps()
          .includeSleepSessions()
          .build()
        Fitness.getSessionsClient(activity!!.applicationContext, googleSignInAccount)
          .readSession(request)
          .addOnSuccessListener(threadPoolExecutor!!, sleepDataHandler(type, result))
          .addOnFailureListener(errHandler(result))

      }
      DataType.TYPE_ACTIVITY_SEGMENT -> {
        val readRequest: SessionReadRequest
        val readRequestBuilder = SessionReadRequest.Builder()
            .setTimeInterval(startTime, endTime, TimeUnit.MILLISECONDS)
            .enableServerQueries()
            .readSessionsFromAllApps()
            .includeActivitySessions()
            .read(type.dataType)
            .read(DataType.TYPE_CALORIES_EXPENDED)

        // If fine location is enabled, read distance data
        if (ContextCompat.checkSelfPermission(
            activity!!.applicationContext,
            android.Manifest.permission.ACCESS_FINE_LOCATION
          ) == PackageManager.PERMISSION_GRANTED
        ) {
          // Request permission with distance data. 
          // Google Fit requires this when we query for distance data
          // as it is restricted data
          if (!GoogleSignIn.hasPermissions(googleSignInAccount, fitnessOptions)) {
            GoogleSignIn.requestPermissions(
              activity!!, // your activity
              HealthConstants.GOOGLE_FIT_PERMISSIONS_REQUEST_CODE,
              googleSignInAccount,
              fitnessOptions
            )
          }
          readRequestBuilder.read(DataType.TYPE_DISTANCE_DELTA)
        } 
        readRequest = readRequestBuilder.build()
        Fitness.getSessionsClient(activity!!.applicationContext, googleSignInAccount)
          .readSession(readRequest)
          .addOnSuccessListener(threadPoolExecutor!!, workoutDataHandler(type, result))
          .addOnFailureListener(errHandler(result))
      }
      else -> {
        Fitness.getHistoryClient(activity!!.applicationContext, googleSignInAccount)
          .readData(
            DataReadRequest.Builder()
              .read(type.dataType)
              .setTimeRange(startTime, endTime, TimeUnit.MILLISECONDS)
              .build()
          )
          .addOnSuccessListener(threadPoolExecutor!!, dataHandler(type, result))
          .addOnFailureListener(errHandler(result))
      }
    }

  }

  private fun dataHandler(type: HealthDataType, result: Result) =
    OnSuccessListener { response: DataReadResponse ->
      /// Fetch all data points for the specified DataType
      val dataSet = response.getDataSet(type.dataType)
      /// For each data point, extract the contents and send them to Flutter, along with date and unit.
      val healthData = dataSet.dataPoints.mapIndexed { _, dataPoint ->
        val source = dataPoint.originalDataSource
        hashMapOf(
          "value" to getHealthDataValue(dataPoint, type.field),
          "date_from" to dataPoint.getStartTime(TimeUnit.MILLISECONDS),
          "date_to" to dataPoint.getEndTime(TimeUnit.MILLISECONDS),
          "source_name" to (source.appPackageName ?: source.device?.model ?: ""),
          "source_id" to source.streamIdentifier
        )
      }
      activity!!.runOnUiThread { result.success(healthData) }
    }

  private fun errHandler(result: Result) = OnFailureListener { exception ->
    activity!!.runOnUiThread { result.success(null) }
    Log.i("FLUTTER_HEALTH::ERROR", exception.message ?: "unknown error")
    Log.i("FLUTTER_HEALTH::ERROR", exception.stackTrace.toString())
  }

  private fun sleepDataHandler(type: HealthDataType, result: Result) =
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
                if (dataPoint.getValue(Field.FIELD_SLEEP_SEGMENT_TYPE)
                    .asInt() != 3
                ) {
                  healthData.add(
                    hashMapOf(
                      "value" to dataPoint.getEndTime(TimeUnit.MINUTES) - dataPoint.getStartTime(
                        TimeUnit.MINUTES
                      ),
                      "date_from" to dataPoint.getStartTime(TimeUnit.MILLISECONDS),
                      "date_to" to dataPoint.getEndTime(TimeUnit.MILLISECONDS),
                      "unit" to "MINUTES",
                      "source_name" to (dataPoint.originalDataSource.appPackageName
                        ?: (dataPoint.originalDataSource.device?.model
                          ?: "unknown")),
                      "source_id" to dataPoint.originalDataSource.streamIdentifier
                    )
                  )
                }
              }
            }
          } else {
            healthData.add(
              hashMapOf(
                "value" to session.getEndTime(TimeUnit.MINUTES) - session.getStartTime(
                  TimeUnit.MINUTES
                ),
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
                healthData.add(
                  hashMapOf(
                    "value" to dataPoint.getEndTime(TimeUnit.MINUTES) - dataPoint.getStartTime(
                      TimeUnit.MINUTES
                    ),
                    "date_from" to dataPoint.getStartTime(TimeUnit.MILLISECONDS),
                    "date_to" to dataPoint.getEndTime(TimeUnit.MILLISECONDS),
                    "unit" to "MINUTES",
                    "source_name" to (dataPoint.originalDataSource.appPackageName
                      ?: (dataPoint.originalDataSource.device?.model
                        ?: "unknown")),
                    "source_id" to dataPoint.originalDataSource.streamIdentifier
                  )
                )
              }
            }
          }
        }
      }
      activity!!.runOnUiThread { result.success(healthData) }
    }

  private fun workoutDataHandler(type: HealthDataType, result: Result) =
    OnSuccessListener { response: SessionReadResponse ->
      val healthData: MutableList<Map<String, Any?>> = mutableListOf()
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
            "workoutActivityType" to HealthConstants.workoutTypeMap.filterValues { it == session.activity }.keys.first(),
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
      activity!!.runOnUiThread { result.success(healthData) }
    }

  private fun callToHealthTypes(call: MethodCall): FitnessOptions {
    val typesBuilder = FitnessOptions.builder()
    val types = call.argument<List<String>>("types")!!.map(HealthDataType::fromString)
    val permissions = call.argument<List<Int>>("permissions")!!
    assert(types.count() == permissions.count())

    for ((i, typeKey) in types.withIndex()) {
      val access = permissions[i]
      when (access) {
        0 -> typesBuilder.addDataType(typeKey.dataType, FitnessOptions.ACCESS_READ)
        1 -> typesBuilder.addDataType(typeKey.dataType, FitnessOptions.ACCESS_WRITE)
        2 -> {
          typesBuilder.addDataType(typeKey.dataType, FitnessOptions.ACCESS_READ)
          typesBuilder.addDataType(typeKey.dataType, FitnessOptions.ACCESS_WRITE)
        }
        else -> throw IllegalArgumentException("Unknown access type $access")
      }
      if (typeKey == HealthDataType.SLEEP_ASLEEP || typeKey == HealthDataType.SLEEP_AWAKE || typeKey == HealthDataType.SLEEP_IN_BED || typeKey == HealthDataType.WORKOUT) {
        typesBuilder.accessSleepSessions(FitnessOptions.ACCESS_READ)
        when (access) {
          0 -> typesBuilder.accessSleepSessions(FitnessOptions.ACCESS_READ)
          1 -> typesBuilder.accessSleepSessions(FitnessOptions.ACCESS_WRITE)
          2 -> {
            typesBuilder.accessSleepSessions(FitnessOptions.ACCESS_READ)
            typesBuilder.accessSleepSessions(FitnessOptions.ACCESS_WRITE)
          }
          else -> throw IllegalArgumentException("Unknown access type $access")
        }
      }

    }
    return typesBuilder.build()
  }

  private fun hasPermissions(call: MethodCall, result: Result) {

    if (activity == null) {
      result.success(false)
      return
    }

    val optionsToRegister = callToHealthTypes(call)
    mResult = result

    val isGranted = GoogleSignIn.hasPermissions(
      GoogleSignIn.getLastSignedInAccount(activity!!),
      optionsToRegister
    )

    mResult?.success(isGranted)
  }

  /// Called when the "requestAuthorization" is invoked from Flutter
  private fun requestAuthorization(call: MethodCall, result: Result) {
    if (activity == null) {
      result.success(false)
      return
    }

    val optionsToRegister = callToHealthTypes(call)
    mResult = result

    val isGranted = GoogleSignIn.hasPermissions(
      GoogleSignIn.getLastSignedInAccount(activity!!),
      optionsToRegister
    )
    /// Not granted? Ask for permission
    if (!isGranted && activity != null) {
      GoogleSignIn.requestPermissions(
        activity!!,
        HealthConstants.GOOGLE_FIT_PERMISSIONS_REQUEST_CODE,
        GoogleSignIn.getLastSignedInAccount(activity!!),
        optionsToRegister
      )
    }
    /// Permission already granted
    else {
      mResult?.success(true)
    }
  }

  private fun getTotalStepsInInterval(call: MethodCall, result: Result) {
    val start = call.argument<Long>("startTime")!!
    val end = call.argument<Long>("endTime")!!

    val activity = activity ?: return

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
      .addOnSuccessListener(
        threadPoolExecutor!!,
        getStepsInRange(start, end, aggregatedDataType, result)
      )

  }


  private fun getStepsInRange(
    start: Long,
    end: Long,
    aggregatedDataType: DataType,
    result: Result
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
      activity!!.runOnUiThread {
        result.success(map.values.firstOrNull())
      }
    }

  /// Handle calls from the MethodChannel
  override fun onMethodCall(call: MethodCall, result: Result) {
    when (call.method) {
      "requestAuthorization" -> requestAuthorization(call, result)
      "getData" -> getData(call, result)
      "writeData" -> writeData(call, result)
      "getTotalStepsInInterval" -> getTotalStepsInInterval(call, result)
      "hasPermissions" -> hasPermissions(call, result)
      "writeWorkoutData" -> writeWorkoutData(call, result)
      else -> result.notImplemented()
    }
  }

  override fun onAttachedToActivity(binding: ActivityPluginBinding) {
    if (channel == null) {
      return
    }
    binding.addActivityResultListener(this)
    activity = binding.activity
  }

  override fun onDetachedFromActivityForConfigChanges() {
    onDetachedFromActivity()
  }

  override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
    onAttachedToActivity(binding)
  }

  override fun onDetachedFromActivity() {
    if (channel == null) {
      return
    }
    activity = null
  }
}
