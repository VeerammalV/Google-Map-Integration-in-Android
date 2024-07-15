package com.example.locationfinder

import android.Manifest
import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.example.locationfinder.databinding.ActivityMapsBinding
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.LocationSettingsResponse
import com.google.android.gms.location.Priority
import com.google.android.gms.location.SettingsClient
import com.google.android.gms.tasks.CancellationToken
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.OnTokenCanceledListener
import com.google.android.gms.tasks.Task

class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var binding: ActivityMapsBinding
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMapsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
        // Initialize fusedLocationClient
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
    }



    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 1000)
            return
        }

        mMap.isMyLocationEnabled = true
        if (Utility.isInternetAccessAvailable(this) && Utility.isGPSEnabled(this)) {
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location: Location? ->
                    if (location != null) {
                        Log.e("Map", "Latitude: ${location.latitude}, Longitude: ${location.longitude}")

                        val currentLatLng = LatLng(location.latitude, location.longitude)
//                        val markerIcon = BitmapDescriptorFactory.fromResource(R.drawable.pin)

                        mMap.addMarker(
                            MarkerOptions()
                                .position(currentLatLng)
                                .title("Your Current Location")
//                                .icon(markerIcon)
                        )
                        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f))
                    } else {
                        Log.e("Map", "Location is null. Requesting location updates...")
                        getCurrentLocation()
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("Map", "Failed to get location: ${e.message}")
                    Toast.makeText(this, "Failed to get location", Toast.LENGTH_SHORT).show()
                }
        } else {
            Toast.makeText(this, "No internet connection", Toast.LENGTH_SHORT).show()
            enableGPS()
        }
    }

    private fun enableGPS() {
        // retrieves the system's location service
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        // checks whether the GPS provider is enabled on the device
        val isGPSEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)

        // GPS is not enabled, so we need to request the user to enable it.
        // Build a location request to check and prompt user to enable GPS.
        if (isGPSEnabled) { locationPermissionGranted()

        } else {
            val locationRequest = LocationRequest.create().apply {
                interval = 10000
                fastestInterval = 5000
            }

            // Build a location settings request to check if GPS settings meet the requirements.
            val builder = LocationSettingsRequest.Builder()
                .addLocationRequest(locationRequest)
                .setAlwaysShow(true)
            // Get the client for checking location settings
            val client: SettingsClient = LocationServices.getSettingsClient(this)
            // Task to check the location settings
            val task: Task<LocationSettingsResponse> = client.checkLocationSettings(builder.build())

            task.addOnSuccessListener { }

            task.addOnFailureListener { exception ->

                if (exception is ResolvableApiException) {
                    // Location settings are not satisfied, but this can be fixed
                    // by showing the user a dialog.
                    try {
                        // Show the dialog by calling startResolutionForResult(),
                        // and check the result in onActivityResult().
                        exception.startResolutionForResult(this, 30)

                    } catch (sendEx: IntentSender.SendIntentException) {
                        // Ignore the error.
                    }
                }
            }
        }
    }

    private fun locationPermissionGranted() {}

    private fun getCurrentLocation() {

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) { // Precise location
            // Build a request for getting the current location with high accuracy.
            val request = CurrentLocationRequest.Builder().apply {
                setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            }.build()
            // Use fused location client to get the current location.
            fusedLocationClient.getCurrentLocation(request, object : CancellationToken() {
                override fun onCanceledRequested(p0: OnTokenCanceledListener): CancellationToken {
                    return CancellationTokenSource().token
                }

                override fun isCancellationRequested(): Boolean {
                    return false
                }
            })
                .addOnSuccessListener { location ->

                    if (location == null) {
                        gotCurrentLocation(false, null)
                        return@addOnSuccessListener
                    }
                    gotCurrentLocation(true, location)
                    getLocationUpdate()
                }
                .addOnFailureListener {
                    gotCurrentLocation(false, null)
                }

        } else if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) { // Approximate location

            fusedLocationClient.lastLocation
                .addOnSuccessListener { location: Location? ->

                    if (location == null) {
                        gotCurrentLocation(false, null)
                        return@addOnSuccessListener
                    }
                    gotCurrentLocation(true, location)
                    getLocationUpdate()
                }
                .addOnFailureListener {
                    gotCurrentLocation(false, null)
                }
        }
    }

    private fun getLocationUpdate() {

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
            .apply {
                setWaitForAccurateLocation(true)
                setMinUpdateIntervalMillis(10000)
                setMaxUpdateDelayMillis(5000)
            }.build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)

                locationResult.lastLocation?.let {

                } ?: {
                    Log.e("TAG", "Location information isn't available.")
                }
            }
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback!!, Looper.getMainLooper())
        }
    }

    private fun gotCurrentLocation(status: Boolean?, location: Location?) { }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 1000) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                onMapReady(mMap)
                Log.e("Map", "Permission granted")
            } else {
                Log.e("Map", "Permission denied")
            }
        }
    }

}