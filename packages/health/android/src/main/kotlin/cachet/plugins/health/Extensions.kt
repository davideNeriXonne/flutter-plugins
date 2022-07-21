package cachet.plugins.health

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.fitness.data.DataType
import com.google.android.gms.fitness.request.DataReadRequest
import com.google.android.gms.fitness.request.SessionReadRequest

fun DataType.checkPermissions(context: Context) = HealthConstants.dataTypePermissionsMap[this]
    ?.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
    ?: true

fun DataReadRequest.Builder.readIfGranted(context: Context, dataType: DataType) =
    if (dataType.checkPermissions(context)) read(dataType) else this

fun SessionReadRequest.Builder.readIfGranted(context: Context, dataType: DataType) =
    if (dataType.checkPermissions(context)) read(dataType) else this