package com.jinvita.testredis

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.method.ScrollingMovementMethod
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.jinvita.testredis.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    private val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }

    companion object {
        private const val TAG: String = "MainActivity"
    }

    private val logList by lazy { mutableListOf<String>() }
    private lateinit var redisReceiver: BroadcastReceiver
    private lateinit var redisFilter: IntentFilter
    private var channel = ""
    private var redisHost = ""
    private var redisPort = 0


    override fun onStart() {
        AppData.debug(TAG, "onStart called.")
        super.onStart()
        registerReceiver(redisReceiver, redisFilter)
    }

    override fun onStop() {
        AppData.debug(TAG, "onStop called.")
        super.onStop()
        unregisterReceiver(redisReceiver)
    }

    override fun onDestroy() {
        super.onDestroy()
        commandToRedis(Extras.DISCONNECT)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        initView()
        initReceiver()
        // 백그라운드에서 액티비티를 실행하기 위한 다른 앱 위에 표시 권한 체크
        if (!Settings.canDrawOverlays(this))
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${packageName}")))
    }

    private fun initView() = with(binding) {
        // view 에 값 세팅
        idEditText.setText("TEST01")
//        ipEditText.setText("192.168.1.1")
//        portEditText.setText("6379")
        ipEditText.setText("119.70.192.165")
        portEditText.setText("6379")
//        ipEditText.setText("119.6.3.91")
//        portEditText.setText("40020")

        // 로그 뷰에 스크롤 생성
        logTextView.movementMethod = ScrollingMovementMethod()

        initButton()
    }

    // 버튼 초기화
    private fun ActivityMainBinding.initButton() {
        // id 를 누르면 해당 채널로 메시지 보내기
        idTextView.setOnClickListener {
            arrayListOf(
                "TEST01",
                "TEST02",
                "TEST03",
                "TEST04",
                "TEST05",
                "TEST06",
                "TEST07",
            ).apply {
                @SuppressLint("SimpleDateFormat")
                val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date())
                forEach { sendData(it, "checked! from $channel $now") }
                printLog("CHECK : $this")
            }
        }
        connectButton.setOnClickListener { setValueAndConnectRedis() }
        disconnectButton.setOnClickListener {
            idTextView.text = "연결이 없습니다."
            commandToRedis(Extras.DISCONNECT)
        }
    }

    // 변수 검증
    private fun ActivityMainBinding.setValueAndConnectRedis() {
        if (!Settings.canDrawOverlays(this@MainActivity)) {
            AppData.showToast(this@MainActivity, "다른 앱 위에 표시 권한을 허용하세요")
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${packageName}"))
            startActivity(intent)
            return
        }
        channel = idEditText.text.trim().toString()
        redisHost = ipEditText.text.trim().toString()
        redisPort = portEditText.text.trim().toString().toIntOrNull() ?: kotlin.run {
            with("PORT 를 확인해주세요") {
                AppData.showToast(this@MainActivity, this)
                idTextView.text = this
            }
            return
        }
        // view 에 값 세팅
        idTextView.text = "$channel connecting..."
        commandToRedis(Extras.CONNECT)
    }

    // 리시버 초기화
    private fun initReceiver() {
        AppData.debug(TAG, "initReceiver called.")
        redisFilter = IntentFilter()
        redisFilter.addAction(AppData.ACTION_REDIS_DATA)
        redisReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = setReceivedData(intent)
        }
    }

    // 리시버로 받은 데이터 로그로 보여주기
    fun setReceivedData(intent: Intent) {
        val command = intent.getStringExtra(Extras.COMMAND)
        val channel = intent.getStringExtra(Extras.CHANNEL)
        val data = intent.getStringExtra(Extras.DATA)
        AppData.debug(TAG, "$command : $channel - $data")
        printLog("$command : $channel - $data")
        if (channel == this.channel) binding.idTextView.text = "${this.channel} connected ! !"
        data?.apply {
            AppData.showToast(this@MainActivity, this)
            when {
                startsWith("already connected") -> return
                startsWith("check redis connection") -> return
                startsWith("successfully connected") -> return

                equals("fail to connect") ->
                    binding.idTextView.text = "IP 와 PORT 를 확인해주세요"

                equals("fail to reconnect") ->
                    binding.idTextView.text = "네트워크 상태를 확인해주세요"

                else -> setData(this)
            }
        }
    }

    private fun setData(data: String) {
        // TODO: 레디스로부터 받은 메시지 처리 로직 작성 
    }

    // 택스트뷰 스크롤 맨 아래로 내리기
    private fun moveToBottom(textView: TextView) = textView.post {
        try {
            textView.layout.getLineTop(textView.lineCount) - textView.height
        } catch (_: NullPointerException) {
            0
        }.let {
            if (it > 0) textView.scrollTo(0, it)
            else textView.scrollTo(0, 0)
        }
    }

    // 레디스 연결
    private fun commandToRedis(command: String) = with(Intent(this, RedisService::class.java)) {
        if (redisHost.isBlank() || redisPort == 0) {
            if (command == Extras.CONNECT) with("IP 와 PORT 를 확인해주세요") {
                AppData.showToast(this@MainActivity, this)
                binding.idTextView.text = this
            }
            return@with
        }
        putExtra(Extras.COMMAND, command)
        if (command == Extras.CONNECT) {
            putExtra(Extras.REDIS_ACTION, AppData.ACTION_REDIS_DATA)
            putExtra(Extras.REDIS_HOST, redisHost)
            putExtra(Extras.REDIS_PORT, redisPort)
            putExtra(Extras.MY_CHANNEL, channel)
        }
        startForegroundService(this)
    }

    // 특정 channel 에 data 를 보내고 싶을 때
    private fun sendData(channel: String, data: String) = with(Intent(this, RedisService::class.java)) {
        putExtra(Extras.COMMAND, "send")
        putExtra(Extras.CHANNEL, channel)
        putExtra(Extras.DATA, data)
        startForegroundService(this)
    }

    // 로그 찍기
    private fun printLog(message: String) = runOnUiThread {
        @SuppressLint("SimpleDateFormat")
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date())
        val log = "[$now] $message"
        if (logList.size > 1000) logList.removeAt(1)
        logList.add(log)
        val sb = StringBuilder()
        logList.forEach { sb.appendLine(it) }
        binding.logTextView.run {
            text = sb
            moveToBottom(this)
        }
    }
}