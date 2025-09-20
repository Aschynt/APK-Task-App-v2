package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.navigation.NavigationView
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import androidx.drawerlayout.widget.DrawerLayout
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.databinding.ActivityMainBinding
import com.example.myapplication.ui.auth.LoginActivity
import com.example.myapplication.ui.settings.SettingsActivity
import com.example.myapplication.ui.tasks.AddEditTaskActivity
import com.example.myapplication.ui.tasks.TasksFragment
import com.google.firebase.auth.FirebaseAuth

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()

        // Check if user is logged in, if not redirect to login
        if (auth.currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.appBarMain.toolbar)

        setupFAB()
        setupNavigation()
    }

    private fun setupFAB() {
        binding.appBarMain.fab.setOnClickListener { view ->
            // Check which fragment is currently displayed
            val currentFragment = supportFragmentManager.primaryNavigationFragment?.childFragmentManager?.fragments?.get(0)

            when (currentFragment) {
                is TasksFragment -> {
                    // If we're on tasks fragment, add new task
                    currentFragment.addNewTask()
                }
                else -> {
                    // Default action for other fragments
                    Snackbar.make(view, "Add new item", Snackbar.LENGTH_LONG)
                        .setAction("Action", null)
                        .setAnchorView(R.id.fab).show()
                }
            }
        }
    }

    private fun setupNavigation() {
        val drawerLayout: DrawerLayout = binding.drawerLayout
        val navView: NavigationView = binding.navView
        val navController = findNavController(R.id.nav_host_fragment_content_main)

        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.nav_home, R.id.nav_tasks, R.id.nav_reminders
            ), drawerLayout
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        // Update FAB icon based on current destination
        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                R.id.nav_tasks -> {
                    binding.appBarMain.fab.setImageResource(android.R.drawable.ic_input_add)
                    binding.appBarMain.fab.show()
                }
                R.id.nav_home -> {
                    binding.appBarMain.fab.setImageResource(android.R.drawable.ic_input_add)
                    binding.appBarMain.fab.show()
                }
                R.id.nav_reminders -> {
                    binding.appBarMain.fab.setImageResource(android.R.drawable.ic_input_add)
                    binding.appBarMain.fab.show()
                }
                else -> {
                    binding.appBarMain.fab.hide()
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }
}