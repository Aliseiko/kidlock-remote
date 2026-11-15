package uk.telegramgames.kidlock

import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import uk.telegramgames.kidlock.BuildConfig

class AdminActivity : AppCompatActivity() {
    private lateinit var viewModel: AdminViewModel

    private lateinit var btnExit: Button
    private lateinit var rvSections: RecyclerView
    private lateinit var sectionAdapter: AdminSectionAdapter

    private val tabButtons = mutableListOf<Button>()
    private val tabSectionIds = mutableListOf<Int>()
    
    private lateinit var layoutSectionDashboard: View
    private lateinit var layoutSectionSettings: View
    private lateinit var layoutSectionAccess: View
    private lateinit var layoutSectionSystem: View
    private lateinit var layoutSectionSchedule: View
    private lateinit var layoutSectionAppLimits: View
    private lateinit var layoutSectionAbout: View
    
    // Schedule UI
    private lateinit var switchScheduleEnabled: Switch
    private lateinit var rvTimeWindows: RecyclerView
    private lateinit var tvNoWindows: TextView
    private lateinit var btnAddWindow: Button
    private lateinit var timeWindowAdapter: TimeWindowAdapter

    // App limits UI
    private lateinit var rvAppLimits: RecyclerView
    private lateinit var tvNoAppLimits: TextView
    private lateinit var btnAddAppLimit: Button
    private lateinit var appLimitAdapter: AppLimitAdapter
    
    // Device Admin UI
    private lateinit var tvDeviceAdminStatus: TextView
    private lateinit var btnActivateDeviceAdmin: Button
    
    private lateinit var tvRemainingTime: TextView
    private lateinit var tvLockStatus: TextView
    private lateinit var btnUnlock: Button
    
    private lateinit var btnSetLimit: Button
    private lateinit var switchAutostart: Switch
    private lateinit var switchBlocking: Switch
    private lateinit var switchTimerEnabled: Switch
    
    private lateinit var etCodeCount: EditText
    private lateinit var etMinutesPerCode: EditText
    private lateinit var btnGenerateCodes: Button
    private lateinit var etNewPin: EditText
    private lateinit var btnChangePin: Button
    private lateinit var rvCodes: RecyclerView
    private lateinit var codeAdapter: CodeAdapter
    
    private lateinit var tvAccessibilityStatus: TextView
    private lateinit var tvAccessibilityInstructions: TextView
    private lateinit var btnOpenAccessibilitySettings: Button
    private lateinit var tvUsageStatsStatus: TextView
    private lateinit var tvUsageStatsInstructions: TextView
    private lateinit var btnOpenUsageStatsSettings: Button
    
    private lateinit var tvOverlayStatus: TextView
    private lateinit var tvOverlayInstructions: TextView
    private lateinit var btnOpenOverlaySettings: Button
    
    private lateinit var tvAboutVersion: TextView
    
    private lateinit var tvMessage: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin)

        viewModel = ViewModelProvider(this)[AdminViewModel::class.java]

        initViews()
        setupSections()
        setupRecyclerView()
        setupObservers()
        setupClickListeners()
        
        Log.d("KidLock", "AdminActivity.onCreate() - loadCodes called")
        viewModel.loadCodes()

        setupPrimaryButtons()
    }

    private fun setupPrimaryButtons() {
        setupPrimaryButton(btnSetLimit)
        setupPrimaryButton(btnUnlock)
        
        setupPrimaryButton(btnGenerateCodes)
        setupPrimaryButton(btnChangePin)
        
        setupPrimaryButton(btnOpenAccessibilitySettings)
        setupPrimaryButton(btnOpenUsageStatsSettings)
        setupPrimaryButton(btnOpenOverlaySettings)
        
        setupPrimaryButton(btnExit)
    }

    private fun setupPrimaryButton(button: Button) {
        button.backgroundTintList = null
        button.isFocusable = true
        button.isClickable = true
    }

    private fun initViews() {
        btnExit = findViewById(R.id.btnExit)

        rvSections = findViewById(R.id.rvSections)

        layoutSectionDashboard = findViewById(R.id.layout_section_dashboard)
        layoutSectionSettings = findViewById(R.id.layout_section_settings)
        layoutSectionAccess = findViewById(R.id.layout_section_access)
        layoutSectionSystem = findViewById(R.id.layout_section_system)
        layoutSectionSchedule = findViewById(R.id.layout_section_schedule)
        layoutSectionAppLimits = findViewById(R.id.layout_section_app_limits)
        layoutSectionAbout = findViewById(R.id.layout_section_about)

        tvRemainingTime = findViewById(R.id.tvRemainingTime)
        tvLockStatus = findViewById(R.id.tvLockStatus)
        btnUnlock = findViewById(R.id.btnUnlock)

        btnSetLimit = findViewById(R.id.btnSetLimit)
        switchAutostart = findViewById(R.id.switchAutostart)
        switchBlocking = findViewById(R.id.switchBlocking)
        switchTimerEnabled = findViewById(R.id.switchTimerEnabled)

        etCodeCount = findViewById(R.id.etCodeCount)
        etMinutesPerCode = findViewById(R.id.etMinutesPerCode)
        btnGenerateCodes = findViewById(R.id.btnGenerateCodes)
        etNewPin = findViewById(R.id.etNewPin)
        btnChangePin = findViewById(R.id.btnChangePin)
        rvCodes = findViewById(R.id.rvCodes)

        tvAccessibilityStatus = findViewById(R.id.tvAccessibilityStatus)
        tvAccessibilityInstructions = findViewById(R.id.tvAccessibilityInstructions)
        btnOpenAccessibilitySettings = findViewById(R.id.btnOpenAccessibilitySettings)
        tvUsageStatsStatus = findViewById(R.id.tvUsageStatsStatus)
        tvUsageStatsInstructions = findViewById(R.id.tvUsageStatsInstructions)
        btnOpenUsageStatsSettings = findViewById(R.id.btnOpenUsageStatsSettings)

        tvOverlayStatus = findViewById(R.id.tvOverlayStatus)
        tvOverlayInstructions = findViewById(R.id.tvOverlayInstructions)
        btnOpenOverlaySettings = findViewById(R.id.btnOpenOverlaySettings)

        tvAboutVersion = findViewById(R.id.tvAboutVersion)

        // Schedule UI
        switchScheduleEnabled = findViewById(R.id.switchScheduleEnabled)
        rvTimeWindows = findViewById(R.id.rvTimeWindows)
        tvNoWindows = findViewById(R.id.tvNoWindows)
        btnAddWindow = findViewById(R.id.btnAddWindow)

        // App limits UI
        rvAppLimits = findViewById(R.id.rvAppLimits)
        tvNoAppLimits = findViewById(R.id.tvNoAppLimits)
        btnAddAppLimit = findViewById(R.id.btnAddAppLimit)

        // Device Admin UI
        tvDeviceAdminStatus = findViewById(R.id.tvDeviceAdminStatus)
        btnActivateDeviceAdmin = findViewById(R.id.btnActivateDeviceAdmin)
        
        // Set app version
        tvAboutVersion.text = getString(R.string.about_version_format, BuildConfig.VERSION_NAME)

        tvMessage = findViewById(R.id.tvMessage)

        setupTabs()

        setupEditTextKeyboard(etCodeCount)
        setupEditTextKeyboard(etMinutesPerCode)
        setupEditTextKeyboard(etNewPin)
    }

    private fun setupTabs() {
        val tabIds = listOf(
            R.id.tab_schedule to 7,
            R.id.tab_app_limits to 8,
            R.id.tab_dashboard to 1,
            R.id.tab_access to 3,
            R.id.tab_settings to 2,
            R.id.tab_system to 4,
            R.id.tab_about to 6
        )
        for ((tabId, sectionId) in tabIds) {
            val view = findViewById<View>(tabId) ?: continue
            val tab = view as? Button ?: continue
            tab.isSelected = false
            tab.setOnClickListener {
                updateSectionVisibility(sectionId)
            }
            tabButtons.add(tab)
            tabSectionIds.add(sectionId)
        }
    }

    private fun highlightActiveTab(sectionId: Int) {
        for (i in tabButtons.indices) {
            tabButtons[i].isSelected = tabSectionIds[i] == sectionId
        }
    }

    private fun setupEditTextKeyboard(editText: EditText) {
        editText.setOnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
            }
        }
        editText.setOnClickListener {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(it, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun setupSections() {
        // Ordre voulu : Horaires (7) en premier, puis Monitoring/Dashboard (1),
        // puis Accès, Paramètres, Système, À propos, Quitter.
        val sections = listOf(
            AdminSection(7, getString(R.string.section_schedule), true),
            AdminSection(8, getString(R.string.section_app_limits)),
            AdminSection(1, getString(R.string.section_dashboard), true),
            AdminSection(3, getString(R.string.section_access)),
            AdminSection(2, getString(R.string.section_settings)),
            AdminSection(4, getString(R.string.section_system)),
            AdminSection(6, getString(R.string.section_about)),
            AdminSection(5, getString(R.string.exit))
        )

        sectionAdapter = AdminSectionAdapter(sections) { section ->
            if (section.id == 5) {
                finish()
            } else {
                updateSectionVisibility(section.id)
            }
        }

        rvSections.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        rvSections.adapter = sectionAdapter

        updateSectionVisibility(7)
    }

    private fun updateSectionVisibility(sectionId: Int) {
        layoutSectionDashboard.visibility = if (sectionId == 1) View.VISIBLE else View.GONE
        layoutSectionSettings.visibility = if (sectionId == 2) View.VISIBLE else View.GONE
        layoutSectionAccess.visibility = if (sectionId == 3) View.VISIBLE else View.GONE
        layoutSectionSystem.visibility = if (sectionId == 4) View.VISIBLE else View.GONE
layoutSectionSchedule.visibility = if (sectionId == 7) View.VISIBLE else View.GONE
        layoutSectionAppLimits.visibility = if (sectionId == 8) View.VISIBLE else View.GONE
        layoutSectionAbout.visibility = if (sectionId == 6) View.VISIBLE else View.GONE

        highlightActiveTab(sectionId)

        when (sectionId) {
            1 -> btnSetLimit.requestFocus()
            8 -> btnAddAppLimit.requestFocus()
            2 -> etNewPin.requestFocus()
            3 -> {
                val hasCodes = (viewModel.codes.value?.isNotEmpty() == true)
                if (hasCodes) {
                    rvCodes.requestFocus()
                } else {
                    val isPaid = viewModel.isPaidVersion.value ?: false
                    if (isPaid) {
                        etCodeCount.requestFocus()
                    } else {
                        etMinutesPerCode.requestFocus()
                    }
                }
            }
            4 -> btnOpenAccessibilitySettings.requestFocus()
            7 -> btnAddWindow.requestFocus()
        }
    }

    private fun setupRecyclerView() {
        codeAdapter = CodeAdapter(emptyList()) { code ->
            viewModel.deleteCode(code)
        }
        rvCodes.layoutManager = LinearLayoutManager(this)
        rvCodes.adapter = codeAdapter

        timeWindowAdapter = TimeWindowAdapter(
            windows = emptyList(),
            onEdit = { window -> showEditTimeWindowDialog(window) },
            onDelete = { window -> confirmDeleteTimeWindow(window) }
        )
        rvTimeWindows.layoutManager = LinearLayoutManager(this)
        rvTimeWindows.adapter = timeWindowAdapter

        appLimitAdapter = AppLimitAdapter(
            items = emptyList(),
            formatMinutes = { minutes -> TimeManager.formatMinutes(this, minutes) },
            onRemove = { info -> confirmRemoveAppLimit(info) }
        )
        rvAppLimits.layoutManager = LinearLayoutManager(this)
        rvAppLimits.adapter = appLimitAdapter
    }

    private fun setupObservers() {
        viewModel.dailyLimitMinutes.observe(this) { minutes ->
            tvRemainingTime.text = TimeManager.formatMinutes(this, minutes)
        }

        viewModel.remainingTimeMinutes.observe(this) { minutes ->
            val statusColor = if (minutes > 0) getColor(R.color.status_good) else getColor(R.color.status_bad)
            val statusText = if (minutes > 0) {
                getString(R.string.time_remaining_format, TimeManager.formatMinutes(this, minutes))
            } else {
                getString(R.string.time_expired)
            }
            tvLockStatus.text = statusText
            tvLockStatus.setTextColor(statusColor)
        }

        viewModel.codes.observe(this) { codes ->
            codeAdapter.updateCodes(codes)
            if (codes.isNotEmpty()) {
                rvCodes.scrollToPosition(0)
            }
        }

        viewModel.isAccessibilityServiceEnabled.observe(this) { enabled ->
            val statusText = if (enabled) getString(R.string.accessibility_enabled) else getString(R.string.accessibility_disabled)
            tvAccessibilityStatus.text = getString(R.string.accessibility_status_format, statusText)
            tvAccessibilityStatus.setTextColor(if (enabled) getColor(R.color.status_good) else getColor(R.color.status_bad))
            tvAccessibilityInstructions.visibility = if (enabled) View.GONE else View.VISIBLE
        }

        viewModel.isUsageStatsPermissionGranted.observe(this) { granted ->
            val statusText = if (granted) getString(R.string.usage_stats_granted) else getString(R.string.usage_stats_denied)
            tvUsageStatsStatus.text = getString(R.string.usage_stats_status_format, statusText)
            tvUsageStatsStatus.setTextColor(if (granted) getColor(R.color.status_good) else getColor(R.color.status_bad))
            tvUsageStatsInstructions.visibility = if (granted) View.GONE else View.VISIBLE
        }

        viewModel.isOverlayPermissionGranted.observe(this) { granted ->
            val statusText = if (granted) getString(R.string.overlay_granted) else getString(R.string.overlay_denied)
            tvOverlayStatus.text = getString(R.string.overlay_status_format, statusText)
            tvOverlayStatus.setTextColor(if (granted) getColor(R.color.status_good) else getColor(R.color.status_bad))
            tvOverlayInstructions.visibility = if (granted) View.GONE else View.VISIBLE
        }

        viewModel.isAutostartEnabled.observe(this) { enabled ->
            switchAutostart.isChecked = enabled
        }

        viewModel.isBlockingEnabled.observe(this) { enabled ->
            switchBlocking.isChecked = enabled
        }

        viewModel.canEnableBlocking.observe(this) { canEnable ->
            switchBlocking.isEnabled = canEnable
            if (!canEnable && switchBlocking.isChecked) {
                switchBlocking.isChecked = false
            }
        }

        viewModel.isTimerEnabled.observe(this) { enabled ->
            switchTimerEnabled.isChecked = enabled
        }

        viewModel.message.observe(this) { message ->
            tvMessage.text = message ?: ""
            tvMessage.visibility = if (message.isNullOrEmpty()) View.GONE else View.VISIBLE
        }

        viewModel.isPaidVersion.observe(this) { isPaid ->
            updateCodeGenerationUI(isPaid)
        }

        viewModel.isUnlockedUntilTomorrow.observe(this) { isUnlocked ->
            btnUnlock.text = if (isUnlocked) {
                getString(R.string.lock_until_tomorrow)
            } else {
                getString(R.string.unlock_until_tomorrow)
            }
        }

        // Schedule observations
        viewModel.isScheduleEnabled.observe(this) { enabled ->
            switchScheduleEnabled.isChecked = enabled
        }
        viewModel.timeWindows.observe(this) { windows ->
            timeWindowAdapter.updateWindows(windows)
            tvNoWindows.visibility = if (windows.isEmpty()) View.VISIBLE else View.GONE
            rvTimeWindows.visibility = if (windows.isEmpty()) View.GONE else View.VISIBLE
        }
        viewModel.isDeviceAdminActive.observe(this) { active ->
            tvDeviceAdminStatus.text = if (active) {
                getString(R.string.device_admin_active)
            } else {
                getString(R.string.device_admin_inactive)
            }
        }

        viewModel.appLimits.observe(this) { limits ->
            appLimitAdapter.updateItems(limits)
            tvNoAppLimits.visibility = if (limits.isEmpty()) View.VISIBLE else View.GONE
            rvAppLimits.visibility = if (limits.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    private fun updateCodeGenerationUI(isPaid: Boolean) {
        if (isPaid) {
            etCodeCount.visibility = View.VISIBLE
            if (etCodeCount.text.isEmpty()) {
                etCodeCount.setText("30")
            }
            btnGenerateCodes.text = getString(R.string.generate_codes_paid)
        } else {
            etCodeCount.visibility = View.GONE
            btnGenerateCodes.text = getString(R.string.generate_codes_free)
        }
    }

    private fun setupClickListeners() {
        btnSetLimit.setOnClickListener {
            val currentMinutes = viewModel.dailyLimitMinutes.value ?: 60
            val currentHours = currentMinutes / 60
            val currentMins = currentMinutes % 60
            TimePickerDialog(
                this,
                { _, hourOfDay, minute ->
                    val totalMinutes = hourOfDay * 60 + minute
                    viewModel.setDailyLimitMinutes(totalMinutes)
                },
                currentHours,
                currentMins,
                true
            ).show()
        }

        btnGenerateCodes.setOnClickListener {
            val isPaid = viewModel.isPaidVersion.value ?: false
            val count = if (isPaid) {
                etCodeCount.text.toString().toIntOrNull() ?: 30
            } else {
                3
            }
            val minutes = etMinutesPerCode.text.toString().toIntOrNull() ?: 30
            viewModel.generateCodes(count, minutes)
            if (isPaid) {
                etCodeCount.setText("30")
            }
        }

        btnChangePin.setOnClickListener {
            val newPin = etNewPin.text.toString()
            viewModel.changePin(newPin)
            etNewPin.setText("")
        }

        btnUnlock.setOnClickListener {
            viewModel.toggleUnlockUntilTomorrow()
        }

        btnOpenAccessibilitySettings.setOnClickListener {
            tvAccessibilityInstructions.visibility = View.VISIBLE
            viewModel.openAccessibilitySettings()
        }

        btnOpenUsageStatsSettings.setOnClickListener {
            tvUsageStatsInstructions.visibility = View.VISIBLE
            viewModel.openUsageStatsSettings()
        }

        btnOpenOverlaySettings.setOnClickListener {
            tvOverlayInstructions.visibility = View.VISIBLE
            viewModel.openOverlaySettings()
        }

        switchAutostart.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setAutostartEnabled(isChecked)
        }

        switchBlocking.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setBlockingEnabled(isChecked)
        }

        switchTimerEnabled.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setTimerEnabled(isChecked)
        }

        btnExit.setOnClickListener {
            finish()
        }

        // Schedule listener
        switchScheduleEnabled.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setScheduleEnabled(isChecked)
            // Avertissement schedule-only : pas de cap quotidien
            if (isChecked && viewModel.isTimerEnabled.value != true) {
                android.widget.Toast.makeText(this, getString(R.string.schedule_only_warning), android.widget.Toast.LENGTH_LONG).show()
            }
        }

        btnAddWindow.setOnClickListener {
            showAddTimeWindowDialog()
        }

        // Device Admin listener
        btnActivateDeviceAdmin.setOnClickListener {
            viewModel.openDeviceAdminSettings()
        }

        // App limits listener
        btnAddAppLimit.setOnClickListener {
            showAddAppLimitDialog()
        }
    }

    private fun showAddAppLimitDialog() {
        showAppSelectorDialog(emptyList(), title = getString(R.string.app_limits_select_title)) { selectedPackages ->
            if (selectedPackages.isEmpty()) return@showAppSelectorDialog
            showMinutesPickerForApps(selectedPackages)
        }
    }

    private fun showMinutesPickerForApps(packages: List<String>) {
        // Выбор длительности в формате «часы:минуты», как у глобального лимита
        TimePickerDialog(
            this,
            { _, hourOfDay, minute ->
                val minutes = hourOfDay * 60 + minute
                if (minutes <= 0) {
                    viewModel.showMessage(getString(R.string.limit_negative_error))
                    return@TimePickerDialog
                }
                packages.forEach { pkg -> viewModel.setAppLimit(pkg, minutes) }
            },
            0,
            30,
            true
        ).apply {
            setTitle(getString(R.string.app_limits_pick_minutes))
        }.show()
    }

    private fun confirmRemoveAppLimit(info: AdminViewModel.AppLimitInfo) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.app_limit_remove_title))
            .setMessage(getString(R.string.app_limit_remove_message, info.appName))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                viewModel.removeAppLimit(info.packageName)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showAddTimeWindowDialog() {
        showTimePickerDialog(
            initialStartHour = 8,
            initialStartMinute = 0,
            initialEndHour = 20,
            initialEndMinute = 0,
            initialType = TimeWindowType.BLOCK,
            initialBlockAllApps = true,
            initialBlockedApps = emptyList(),
            initialDaysOfWeek = emptyList(),
            title = getString(R.string.schedule_add_window),
            onConfirm = { startH, startM, endH, endM, type, blockAll, blockedApps, daysOfWeek ->
                val window = TimeWindow(
                    startHour = startH,
                    startMinute = startM,
                    endHour = endH,
                    endMinute = endM,
                    type = type,
                    blockAllApps = blockAll,
                    blockedApps = blockedApps,
                    daysOfWeek = daysOfWeek
                )
                viewModel.addTimeWindow(window)
            }
        )
    }

    private fun showEditTimeWindowDialog(window: TimeWindow) {
        showTimePickerDialog(
            initialStartHour = window.startHour,
            initialStartMinute = window.startMinute,
            initialEndHour = window.endHour,
            initialEndMinute = window.endMinute,
            initialType = window.type,
            initialBlockAllApps = window.blockAllApps,
            initialBlockedApps = window.blockedApps,
            initialDaysOfWeek = window.daysOfWeek,
            title = getString(R.string.schedule_edit_window),
            onConfirm = { startH, startM, endH, endM, type, blockAll, blockedApps, daysOfWeek ->
                val updated = window.copy(
                    startHour = startH,
                    startMinute = startM,
                    endHour = endH,
                    endMinute = endM,
                    type = type,
                    blockAllApps = blockAll,
                    blockedApps = blockedApps,
                    daysOfWeek = daysOfWeek
                )
                viewModel.updateTimeWindow(updated)
            }
        )
    }

    private fun showTimePickerDialog(
        initialStartHour: Int,
        initialStartMinute: Int,
        initialEndHour: Int,
        initialEndMinute: Int,
        initialType: TimeWindowType,
        initialBlockAllApps: Boolean,
        initialBlockedApps: List<String>,
        initialDaysOfWeek: List<Int>,
        title: String,
        onConfirm: (startH: Int, startM: Int, endH: Int, endM: Int, type: TimeWindowType, blockAll: Boolean, blockedApps: List<String>, daysOfWeek: List<Int>) -> Unit
    ) {
        var selectedStartHour = initialStartHour
        var selectedStartMinute = initialStartMinute
        var selectedEndHour = initialEndHour
        var selectedEndMinute = initialEndMinute
        var selectedType = initialType
        var selectedBlockAll = initialBlockAllApps
        var selectedBlockedApps = initialBlockedApps.toMutableList()
        var selectedDays = initialDaysOfWeek.toMutableList()

        val typeOptions = arrayOf(
            getString(R.string.schedule_type_block),
            getString(R.string.schedule_type_allow)
        )
        val typeValues = arrayOf(TimeWindowType.BLOCK, TimeWindowType.ALLOW)

        val dayNames = arrayOf("Lun", "Mar", "Mer", "Jeu", "Ven", "Sam", "Dim")
        val dayValues = listOf(1, 2, 3, 4, 5, 6, 7)

        val builder = AlertDialog.Builder(this)
        builder.setTitle(title)

        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }

        val tvStartLabel = android.widget.TextView(this).apply {
            text = getString(R.string.schedule_start_time)
            setTextColor(getColor(R.color.white))
            textSize = 16f
        }
        layout.addView(tvStartLabel)

        val btnStartTime = Button(this).apply {
            text = String.format("%02d:%02d", selectedStartHour, selectedStartMinute)
            setOnClickListener {
                TimePickerDialog(this@AdminActivity, { _, hour, minute ->
                    selectedStartHour = hour
                    selectedStartMinute = minute
                    text = String.format("%02d:%02d", hour, minute)
                }, selectedStartHour, selectedStartMinute, true).show()
            }
        }
        layout.addView(btnStartTime)

        val tvEndLabel = android.widget.TextView(this).apply {
            text = getString(R.string.schedule_end_time)
            setTextColor(getColor(R.color.white))
            textSize = 16f
            setPadding(0, 24, 0, 0)
        }
        layout.addView(tvEndLabel)

        val btnEndTime = Button(this).apply {
            text = String.format("%02d:%02d", selectedEndHour, selectedEndMinute)
            setOnClickListener {
                TimePickerDialog(this@AdminActivity, { _, hour, minute ->
                    selectedEndHour = hour
                    selectedEndMinute = minute
                    text = String.format("%02d:%02d", hour, minute)
                }, selectedEndHour, selectedEndMinute, true).show()
            }
        }
        layout.addView(btnEndTime)

        // Jours de la semaine
        val tvDaysLabel = android.widget.TextView(this).apply {
            text = getString(R.string.schedule_days_label)
            setTextColor(getColor(R.color.white))
            textSize = 16f
            setPadding(0, 24, 0, 0)
        }
        layout.addView(tvDaysLabel)

        val dayCheckBoxes = mutableListOf<android.widget.CheckBox>()
        val daysLayout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(0, 8, 0, 0)
        }

        for (i in dayValues.indices) {
            val cb = android.widget.CheckBox(this).apply {
                text = dayNames[i]
                setTextColor(getColor(R.color.white))
                isChecked = selectedDays.contains(dayValues[i]) || initialDaysOfWeek.isEmpty()
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        if (!selectedDays.contains(dayValues[i])) selectedDays.add(dayValues[i])
                    } else {
                        selectedDays.remove(dayValues[i])
                    }
                }
            }
            dayCheckBoxes.add(cb)
            daysLayout.addView(cb)
        }

        val btnAllDays = Button(this).apply {
            text = getString(R.string.schedule_every_day)
            setOnClickListener {
                selectedDays.clear()
                dayCheckBoxes.forEach { it.isChecked = true }
            }
            textSize = 13f
        }
        daysLayout.addView(btnAllDays)

        layout.addView(daysLayout)

        val tvTypeLabel = android.widget.TextView(this).apply {
            text = getString(R.string.schedule_type_label)
            setTextColor(getColor(R.color.white))
            textSize = 16f
            setPadding(0, 24, 0, 0)
        }
        layout.addView(tvTypeLabel)

        var switchBlockAll: android.widget.Switch? = null
        var btnSelectApps: Button? = null

        val spinner = android.widget.Spinner(this).apply {
            adapter = android.widget.ArrayAdapter(
                this@AdminActivity,
                android.R.layout.simple_spinner_dropdown_item,
                typeOptions
            )
            setSelection(typeValues.indexOf(initialType))
            onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, pos: Int, id: Long) {
                    selectedType = typeValues[pos]
                    updateAppSelectionVisibility(layout, selectedType, btnSelectApps, switchBlockAll)
                }
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            }
        }
        layout.addView(spinner)

        val tvAppsLabel = android.widget.TextView(this).apply {
            text = getString(R.string.schedule_apps_label)
            setTextColor(getColor(R.color.white))
            textSize = 16f
            setPadding(0, 24, 0, 0)
            visibility = if (selectedType == TimeWindowType.BLOCK) View.VISIBLE else View.GONE
        }
        layout.addView(tvAppsLabel)

        switchBlockAll = android.widget.Switch(this).apply {
            text = getString(R.string.schedule_block_all_apps)
            setTextColor(getColor(R.color.white))
            isChecked = selectedBlockAll
            visibility = if (selectedType == TimeWindowType.BLOCK) View.VISIBLE else View.GONE
            setOnCheckedChangeListener { _, isChecked ->
                selectedBlockAll = isChecked
                btnSelectApps?.visibility = if (isChecked || selectedType != TimeWindowType.BLOCK) View.GONE else View.VISIBLE
            }
        }
        layout.addView(switchBlockAll)

        btnSelectApps = Button(this).apply {
            text = getString(R.string.schedule_select_apps, selectedBlockedApps.size)
            setOnClickListener {
                showAppSelectorDialog(selectedBlockedApps) { apps ->
                    selectedBlockedApps = apps.toMutableList()
                    text = getString(R.string.schedule_select_apps, selectedBlockedApps.size)
                }
            }
            visibility = if (selectedType == TimeWindowType.BLOCK && !selectedBlockAll) View.VISIBLE else View.GONE
        }
        layout.addView(btnSelectApps)

        val scrollView = android.widget.ScrollView(this).apply {
            addView(layout)
        }
        builder.setView(scrollView)

        builder.setPositiveButton(android.R.string.ok) { _, _ ->
            val finalDays = if (selectedDays.size == 7 || selectedDays.isEmpty()) emptyList() else selectedDays.sorted()
            onConfirm(selectedStartHour, selectedStartMinute, selectedEndHour, selectedEndMinute, selectedType, selectedBlockAll, selectedBlockedApps, finalDays)
        }
        builder.setNegativeButton(android.R.string.cancel, null)
        builder.show()
    }

    private fun updateAppSelectionVisibility(layout: android.widget.LinearLayout, type: TimeWindowType, btnSelectApps: Button?, switchBlockAll: android.widget.Switch?) {
        val tvAppsLabel = layout.getChildAt(8) as? android.widget.TextView
        tvAppsLabel?.visibility = if (type == TimeWindowType.BLOCK) View.VISIBLE else View.GONE
        switchBlockAll?.visibility = if (type == TimeWindowType.BLOCK) View.VISIBLE else View.GONE
        btnSelectApps?.visibility = if (type == TimeWindowType.BLOCK && switchBlockAll?.isChecked != true) View.VISIBLE else View.GONE
    }

    private fun showAppSelectorDialog(
        initialSelected: List<String>,
        title: String = getString(R.string.schedule_select_apps_title),
        onConfirm: (List<String>) -> Unit
    ) {
        val packageManager = packageManager
        val installedApps = packageManager.getInstalledApplications(0)
            .filter { packageManager.getLaunchIntentForPackage(it.packageName) != null }
            .filter { !isSystemPackage(it.packageName) }
            .map { appInfo ->
                AppInfo(
                    packageName = appInfo.packageName,
                    appName = packageManager.getApplicationLabel(appInfo).toString(),
                    icon = packageManager.getApplicationIcon(appInfo),
                    isSelected = initialSelected.contains(appInfo.packageName)
                )
            }
            .sortedBy { it.appName.lowercase() }

        val adapter = AppSelectorAdapter(installedApps) { _, _ -> }

        val builder = AlertDialog.Builder(this)
        builder.setTitle(title)

        val recyclerView = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@AdminActivity)
            this.adapter = adapter
            setPadding(16, 0, 16, 0)
        }
        builder.setView(recyclerView)

        builder.setPositiveButton(android.R.string.ok) { _, _ ->
            onConfirm(adapter.getSelectedPackages())
        }
        builder.setNegativeButton(android.R.string.cancel, null)
        builder.show()
    }

    private fun confirmDeleteTimeWindow(window: TimeWindow) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.schedule_delete_title))
            .setMessage(getString(R.string.schedule_delete_message, window.formatTime()))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                viewModel.removeTimeWindow(window.id)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun isSystemPackage(packageName: String): Boolean {
        return packageName.startsWith("com.android") ||
                packageName.startsWith("android") ||
                packageName == "com.google.android.tv.settings" ||
                packageName == "com.google.android.leanbacklauncher"
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadSettings()
        viewModel.checkPermissions()
        viewModel.updateRemainingTime()
        viewModel.loadCodes()
        viewModel.loadScheduleSettings()
        viewModel.loadAppLimits()
    }
}

