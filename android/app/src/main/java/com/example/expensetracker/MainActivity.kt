package com.example.expensetracker

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import com.example.expensetracker.ui.ExpenseViewModel

class MainActivity : ComponentActivity() {
    private var connectivityCallback: ConnectivityManager.NetworkCallback? = null
    private lateinit var viewModel: ExpenseViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel = ViewModelProvider(this, ExpenseViewModelFactory(applicationContext))[ExpenseViewModel::class.java]

        setContent {
            ExpenseTrackerApp(viewModel)
        }

        observeConnectivity()
    }

    private fun observeConnectivity() {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        val isConnected = connectivityManager.activeNetworkInfo?.isConnectedOrConnecting == true
        viewModel.updateConnectivity(isConnected)

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                viewModel.updateConnectivity(true)
            }

            override fun onLost(network: Network) {
                viewModel.updateConnectivity(false)
            }
        }
        connectivityManager.registerNetworkCallback(request, callback)
        connectivityCallback = callback
    }

    override fun onDestroy() {
        super.onDestroy()
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        connectivityCallback?.let { connectivityManager.unregisterNetworkCallback(it) }
    }
}
