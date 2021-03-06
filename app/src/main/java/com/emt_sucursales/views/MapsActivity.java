package com.emt_sucursales.views;

import android.annotation.TargetApi;
import android.app.Dialog;
import android.arch.lifecycle.Lifecycle;
import android.arch.lifecycle.LifecycleOwner;
import android.arch.lifecycle.LifecycleRegistry;
import android.arch.lifecycle.Observer;
import android.arch.lifecycle.ViewModelProviders;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;

import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.emt_sucursales.R;
import com.emt_sucursales.Utils.CustomInfoWindowAdapter;
import com.emt_sucursales.viewmodel.Maps_vm;
import com.emt_sucursales.viewmodel.Maps_vm_factory;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.observers.DisposableObserver;
import io.reactivex.observers.DisposableSingleObserver;
import sortingrv.c20.com.coreapp.model.Sucursales;

import static android.Manifest.permission.ACCESS_COARSE_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback, GoogleMap.OnInfoWindowClickListener, LocationListener, LifecycleOwner {
    String TAG = MapsActivity.class.getSimpleName();
    private LifecycleRegistry mLifecycleRegistry;
    private Maps_vm viewModel;
    private GoogleMap mMap;
    private LocationManager locationManager;
    private ArrayList<String> permissionsToRequest;
    private ArrayList<String> permissionsRejected = new ArrayList<>();
    private ArrayList<String> permissions = new ArrayList<>();
    private final static int ALL_PERMISSIONS_RESULT = 101;
    private String provider;
    private Button btnBuscar;
    private String currLat;
    private String currLng;

    int controlLat = 0;
    int controlLng = 0;

    private CompositeDisposable disposable = new CompositeDisposable();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mLifecycleRegistry = new LifecycleRegistry(this);
        mLifecycleRegistry.markState(Lifecycle.State.CREATED);

        Maps_vm_factory factory = new Maps_vm_factory();
        viewModel = ViewModelProviders.of(this, factory).get(Maps_vm.class);


        if (isGooglePlayServicesAvailable(this)) {
            System.out.println("PlayServices instalado");
        } else {
            System.out.println("PlayServices no instalado");
        }


        permissions.add(ACCESS_FINE_LOCATION);
        permissions.add(ACCESS_COARSE_LOCATION);
        permissionsToRequest = findUnAskedPermissions(permissions);


        setContentView(R.layout.activity_maps);

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (permissionsToRequest.size() > 0) {
                requestPermissions(permissionsToRequest.toArray(new String[permissionsToRequest.size()]), ALL_PERMISSIONS_RESULT);
            } else {
                init();
            }
        } else {
            init();
        }

    }

    private void init() {
        btnBuscar = findViewById(R.id.btnBuscar);
        btnBuscar.setOnClickListener(btnBuscarListener);

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        Criteria criteria = new Criteria();
        provider = locationManager.getBestProvider(criteria, false);

        try {
            final Location location = locationManager.getLastKnownLocation(provider);
            if (location != null) {
                System.out.println("Provider " + provider + " seleccionado.");
                onLocationChanged(location);
            } else {
                System.out.println("Location no disponible");
            }

        } catch (SecurityException e) {

            showMessageOKCancel("Estos permisos son obligatorios para la aplicación. Por favor, acepta el permiso.",
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                requestPermissions(permissionsRejected.toArray(new String[permissionsRejected.size()]), ALL_PERMISSIONS_RESULT);
                            }
                        }
                    });
        }


    }

    View.OnClickListener btnBuscarListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            Intent intent = new Intent(MapsActivity.this, BuscarActivity.class);
            intent.putExtra("currLat", currLat);
            intent.putExtra("currLng", currLng);
            MapsActivity.this.startActivity(intent);
        }
    };

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.setOnInfoWindowClickListener(this);

        LatLng currentUserLocation = new LatLng(19, -99);
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentUserLocation, 4.5f));

        /*
        viewModel.getSucursales().observe(this, new Observer<List<Sucursales>>() {
            @Override
            public void onChanged(@Nullable List<Sucursales> sucursales) {
                if (sucursales != null)
                    setMarkers(sucursales);
            }
        });
*/
        disposable.add(
                viewModel.getSurcursales()
                        .subscribeWith(new DisposableObserver<List<Sucursales>>() {

                            @Override
                            public void onNext(List<Sucursales> sucursalesList) {
                                if (sucursalesList != null)
                                    setMarkers(sucursalesList);
                            }

                            @Override
                            public void onError(Throwable e) {
                                Log.e(TAG, "onError: " + e.getMessage());
                            }

                            @Override
                            public void onComplete() {
                                Log.e(TAG, "onComplete: ");
                            }
                        }));


    }

    private void setMarkers(List<Sucursales> sucursales) {
        if (mMap != null) {
            for (int i = 0; i < sucursales.size(); i++) {
                Sucursales sucursal = sucursales.get(i);
                LatLng location = new LatLng(Double.valueOf(sucursal.getLatitud()), Double.valueOf(sucursal.getLongitud()));

                CustomInfoWindowAdapter adapter = new CustomInfoWindowAdapter(MapsActivity.this);
                mMap.setInfoWindowAdapter(adapter);

                if (sucursal.getTipo().equalsIgnoreCase("s")) {

                    Marker marker = mMap.addMarker(new MarkerOptions()
                            .position(location)
                            .icon((BitmapDescriptorFactory.fromResource(R.drawable.bank)))
                            .title("Sucursal: " + sucursal.getNOMBRE())
                            .snippet(sucursal.getDOMICILIO())
                    );
                    marker.setTag(sucursal);


                } else if (sucursal.getTipo().equalsIgnoreCase("c")) {

                    Marker marker = mMap.addMarker(new MarkerOptions()
                            .position(location)
                            .icon((BitmapDescriptorFactory.fromResource(R.drawable.cajero)))
                            .title("ATM:" + sucursal.getNOMBRE() + " " + sucursal.getDOMICILIO())
                            .snippet(sucursal.getDOMICILIO())
                    );
                    marker.setTag(sucursal);

                }
            }
        }
    }

    @Override
    public void onInfoWindowClick(Marker marker) {
        if (marker != null) {
            Sucursales sucursal = (Sucursales) marker.getTag();
            Gson gson = new Gson();
            Intent intent = new Intent(MapsActivity.this, DetailActivity.class);
            intent.putExtra("sucursal", gson.toJson(sucursal));
            MapsActivity.this.startActivity(intent);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            if (locationManager != null) {
                locationManager.requestLocationUpdates(provider, 400, 1, this);
            }
        } catch (SecurityException e) {

        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (locationManager != null)
            locationManager.removeUpdates(MapsActivity.this);

    }

    public void onStart() {
        super.onStart();
        mLifecycleRegistry.markState(Lifecycle.State.STARTED);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mLifecycleRegistry.markState(Lifecycle.State.DESTROYED);
        disposable.clear();
    }

    @Override
    public void onLocationChanged(Location location) {
        int lat = (int) (location.getLatitude());
        int lng = (int) (location.getLongitude());
        currLat = String.valueOf(location.getLatitude());
        currLng = String.valueOf(location.getLongitude());

        Log.d(TAG, "Location " + lat + " " + lng);
        if (controlLat != lat && controlLng != lng) {

            if (mMap != null) {
                LatLng currentUserLocation = new LatLng(location.getLatitude(), location.getLongitude());

                mMap.addMarker(new MarkerOptions().position(currentUserLocation).title("Usted está aquí"));
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentUserLocation, 9.5f));

                controlLat = lat;
                controlLng = lng;
            }
        }
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(String provider) {
        Toast.makeText(this, "Enabled new provider " + provider,
                Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onProviderDisabled(String provider) {
        Toast.makeText(this, "Disabled provider " + provider,
                Toast.LENGTH_SHORT).show();
    }

    @NonNull
    @Override
    public Lifecycle getLifecycle() {
        return mLifecycleRegistry;
    }


    private ArrayList findUnAskedPermissions(ArrayList<String> wanted) {
        ArrayList<String> result = new ArrayList();

        for (String perm : wanted) {
            if (!hasPermission(perm)) {
                result.add(perm);
            }
        }

        return result;
    }

    private boolean hasPermission(String permission) {
        if (canMakeSmores()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                return (checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED);
            }
        }
        return true;
    }

    private boolean canMakeSmores() {
        return (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP_MR1);
    }

    @TargetApi(Build.VERSION_CODES.M)
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {

        switch (requestCode) {

            case ALL_PERMISSIONS_RESULT:
                for (String perms : permissionsToRequest) {
                    if (!hasPermission(perms)) {
                        permissionsRejected.add(perms);
                    }
                }

                if (permissionsRejected.size() > 0) {


                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        if (shouldShowRequestPermissionRationale(permissionsRejected.get(0))) {
                            showMessageOKCancel("Estos permisos son obligatorios para la aplicación. Por favor, acepta el permiso.",
                                    new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                                requestPermissions(permissionsRejected.toArray(new String[permissionsRejected.size()]), ALL_PERMISSIONS_RESULT);
                                            }
                                        }
                                    });
                            return;
                        }
                    }

                } else {
                    init();
                }

                break;
        }

    }

    private void showMessageOKCancel(String message, DialogInterface.OnClickListener okListener) {
        new AlertDialog.Builder(MapsActivity.this)
                .setMessage(message)
                .setPositiveButton("OK", okListener)
                .setNegativeButton("Cancel", null)
                .create()
                .show();
    }

    private boolean isGooglePlayServicesAvailable(Context mContext) {
        GoogleApiAvailability googleApiAvailability = GoogleApiAvailability.getInstance();
        Integer resultCode = googleApiAvailability.isGooglePlayServicesAvailable(mContext);
        if (resultCode != ConnectionResult.SUCCESS) {
            Dialog dialog = googleApiAvailability.getErrorDialog(this, resultCode, 0);
            if (dialog != null) {
                dialog.show();
            }
            return false;
        }
        return true;
    }

}
