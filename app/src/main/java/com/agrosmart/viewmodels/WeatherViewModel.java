package com.agrosmart.viewmodels;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.agrosmart.models.WeatherResponse;
import com.agrosmart.repository.WeatherRepository;

public class WeatherViewModel extends AndroidViewModel {

    private final WeatherRepository repository;
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>(null);
    private final MutableLiveData<WeatherResponse> weatherData = new MutableLiveData<>(null);

    public WeatherViewModel(@NonNull Application application) {
        super(application);
        this.repository = new WeatherRepository(application);
    }

    public LiveData<Boolean> getIsLoading() { return isLoading; }
    public LiveData<String> getErrorMessage() { return errorMessage; }
    public LiveData<WeatherResponse> getWeatherData() { return weatherData; }

    public void fetchWeather(double lat, double lon, String locationName) {
        isLoading.setValue(true);
        errorMessage.setValue(null);

        repository.getWeather(lat, lon, locationName, new WeatherRepository.WeatherCallback() {
            @Override
            public void onSuccess(WeatherResponse weather) {
                isLoading.postValue(false);
                weatherData.postValue(weather);
            }

            @Override
            public void onError(String message) {
                isLoading.postValue(false);
                errorMessage.postValue(message);
            }
        });
    }
}
