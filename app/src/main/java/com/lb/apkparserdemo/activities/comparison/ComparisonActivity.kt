package com.lb.apkparserdemo.activities.comparison

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import com.lb.apkparserdemo.R
import com.lb.apkparserdemo.activities.activity_main.BoundActivity
import com.lb.apkparserdemo.databinding.ActivityComparisonBinding

class ComparisonActivity : BoundActivity<ActivityComparisonBinding>(ActivityComparisonBinding::inflate) {
    private val viewModel: ComparisonViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Force light status bar content (white icons)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(0))
        
        ViewCompat.setOnApplyWindowInsetsListener(binding.appBarLayout) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.recyclerView) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.updatePadding(bottom = systemBars.bottom, left = systemBars.left, right = systemBars.right)
            insets
        }
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
        }
        binding.toolbar.setNavigationOnClickListener { finish() }
        // Ensure the up button is white
        binding.toolbar.navigationIcon?.setTint(android.graphics.Color.WHITE)

        val adapter = ComparisonAdapter()
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        viewModel.items.observe(this) {
            adapter.submitList(it)
            binding.recyclerView.isVisible = true
        }
        viewModel.loading.observe(this) {
            binding.progressBar.isVisible = it
            if (it) binding.recyclerView.isVisible = false
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_comparison, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_toggle_theme) {
            val isDark = AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES
            AppCompatDelegate.setDefaultNightMode(
                if (isDark) AppCompatDelegate.MODE_NIGHT_NO else AppCompatDelegate.MODE_NIGHT_YES
            )
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
