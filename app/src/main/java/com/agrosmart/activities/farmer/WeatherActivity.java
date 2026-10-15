package com.agrosmart.activities.farmer;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.agrosmart.adapters.ForecastAdapter;
import com.agrosmart.databinding.ActivityWeatherBinding;
import com.agrosmart.utils.LocaleHelper;
import com.agrosmart.utils.SessionManager;
import com.agrosmart.viewmodels.WeatherViewModel;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

public class WeatherActivity extends AppCompatActivity {

    private ActivityWeatherBinding binding;
    private WeatherViewModel weatherViewModel;
    private ForecastAdapter forecastAdapter;
    private FusedLocationProviderClient fusedLocationClient;
    private SessionManager sessionManager;

    private final ActivityResultLauncher<String[]> locationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                Boolean fineGranted = result.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false);
                Boolean coarseGranted = result.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false);
                if ((fineGranted != null && fineGranted) || (coarseGranted != null && coarseGranted)) {
                    loadWeatherWithGps();
                } else {
                    loadSelectedCityWeather();
                }
            });

    @Override
    protected void attachBaseContext(Context newBase) {
        SessionManager session = new SessionManager(newBase);
        super.attachBaseContext(LocaleHelper.setLocale(newBase, session.getLanguage()));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityWeatherBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        sessionManager = new SessionManager(this);
        weatherViewModel = new ViewModelProvider(this).get(WeatherViewModel.class);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        setupRecyclerView();
        setupObservers();
        setupListeners();

        // Default immediately to user's selected city (Mumbai by default) so screen is never blank
        loadSelectedCityWeather();

        // Try detecting live GPS in background if permission is granted
        checkLocationPermission();
    }

    private void setupRecyclerView() {
        forecastAdapter = new ForecastAdapter();
        binding.rvForecast.setLayoutManager(new LinearLayoutManager(this));
        binding.rvForecast.setAdapter(forecastAdapter);
    }

    private void setupObservers() {
        weatherViewModel.getIsLoading().observe(this, loading -> binding.swipeRefresh.setRefreshing(loading));

        weatherViewModel.getErrorMessage().observe(this, error -> {
            if (error != null) {
                Toast.makeText(this, error, Toast.LENGTH_LONG).show();
            }
        });

        weatherViewModel.getWeatherData().observe(this, weather -> {
            if (weather != null) {
                binding.tvLocation.setText("📍 " + weather.getCityName());
                binding.tvMainTemp.setText(String.format(Locale.US, "%.0f°C", weather.getTemp()));
                binding.tvConditionDesc.setText(weather.getCondition() + " • " + weather.getDescription());
                binding.tvHumidityValue.setText(weather.getHumidity() + "%");
                binding.tvWindValue.setText(String.format(Locale.US, "%.1f km/h", weather.getWindSpeed()));
                binding.tvRainValue.setText(weather.getRainChance() + "%");
                binding.tvPressureValue.setText(weather.getPressure() + " hPa");

                if (weather.getForecast() != null) {
                    forecastAdapter.setItems(weather.getForecast());
                }
            }
        });
    }

    private void setupListeners() {
        binding.btnBack.setOnClickListener(v -> finish());
        binding.swipeRefresh.setOnRefreshListener(this::loadSelectedCityWeather);
        binding.btnChangeCity.setOnClickListener(v -> showCitySelectionDialog());
    }

    private void showCitySelectionDialog() {
        String[] cities = {
                "Mumbai, Maharashtra (Default)",
                "Pune, Maharashtra",
                "Nashik, Maharashtra",
                "Nagpur, Maharashtra",
                "Chhatrapati Sambhajinagar, Maharashtra",
                "📡 Detect My Live GPS Location"
        };

        new MaterialAlertDialogBuilder(this)
                .setTitle("Select Weather Location")
                .setItems(cities, (dialog, which) -> {
                    if (which == 0) {
                        selectCity("Mumbai, Maharashtra", 19.0760, 72.8777);
                    } else if (which == 1) {
                        selectCity("Pune, Maharashtra", 18.5204, 73.8567);
                    } else if (which == 2) {
                        selectCity("Nashik, Maharashtra", 19.9975, 73.7898);
                    } else if (which == 3) {
                        selectCity("Nagpur, Maharashtra", 21.1458, 79.0882);
                    } else if (which == 4) {
                        selectCity("Chhatrapati Sambhajinagar, Maharashtra", 19.8762, 75.3433);
                    } else if (which == 5) {
                        checkLocationPermission();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void selectCity(String cityName, double lat, double lon) {
        sessionManager.setSelectedCity(cityName);
        weatherViewModel.fetchWeather(lat, lon, cityName);
        Toast.makeText(this, "Location set to: " + cityName, Toast.LENGTH_SHORT).show();
    }

    private void loadSelectedCityWeather() {
        String savedCity = sessionManager.getSelectedCity();
        if (savedCity.contains("Pune")) {
            weatherViewModel.fetchWeather(18.5204, 73.8567, "Pune, Maharashtra");
        } else if (savedCity.contains("Nashik")) {
            weatherViewModel.fetchWeather(19.9975, 73.7898, "Nashik, Maharashtra");
        } else {
            // Default to Mumbai
            weatherViewModel.fetchWeather(19.0760, 72.8777, "Mumbai, Maharashtra");
        }
    }

    private void checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            loadWeatherWithGps();
        } else {
            locationPermissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
        }
    }

    private void loadWeatherWithGps() {
        try {
            fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
                if (location != null) {
                    double lat = location.getLatitude();
                    double lon = location.getLongitude();
                    String detectedCity = reverseGeocode(lat, lon);
                    sessionManager.setSelectedCity(detectedCity);
                    weatherViewModel.fetchWeather(lat, lon, detectedCity);
                } else {
                    loadSelectedCityWeather();
                }
            }).addOnFailureListener(e -> loadSelectedCityWeather());
        } catch (SecurityException e) {
            loadSelectedCityWeather();
        }
    }

    private String reverseGeocode(double lat, double lon) {
        try {
            Geocoder geocoder = new Geocoder(this, Locale.getDefault());
            List<Address> addresses = geocoder.getFromLocation(lat, lon, 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address addr = addresses.get(0);
                String locality = addr.getLocality();
                String adminArea = addr.getAdminArea();
                if (locality != null) {
                    return locality + (adminArea != null ? ", " + adminArea : "");
                }
                String subAdmin = addr.getSubAdminArea();
                if (subAdmin != null) {
                    return subAdmin + (adminArea != null ? ", " + adminArea : "");
                }
            }
        } catch (IOException ignored) {}
        return "Mumbai, Maharashtra";
    }
}
