package com.jinvita.testredis

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
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
    }

    private fun initView() = with(binding) {
        // view 에 값 세팅
//        idEditText.setText("test01")
//        ipEditText.setText("192.168.1.1")
//        portEditText.setText("6379")
        idEditText.setText("test01")
        ipEditText.setText("119.6.3.91")
        portEditText.setText("40020")

        // 로그 뷰에 스크롤 생성
        logTextView.movementMethod = ScrollingMovementMethod()

        initButton()
    }

    // 버튼 초기화
    private fun ActivityMainBinding.initButton() {
        idTextView.setOnClickListener {
            val testId1 = "test01"
            val testId2 = "test02"
            sendData(testId1, "잘 되는지 확인 from ${AppData.ID}")
            sendData(testId2, "잘 되는지 확인 from ${AppData.ID}")
            printLog("$testId1, $testId2 가 잘 되는지 확인")
        }
        connectButton.setOnClickListener { setValueAndConnectRedis() }
        disconnectButton.setOnClickListener {
            idTextView.text = "연결이 없습니다."
            commandToRedis(Extras.DISCONNECT)
        }
    }

    // 변수 검증
    private fun ActivityMainBinding.setValueAndConnectRedis() {
        AppData.ID = idEditText.text.trim().toString()
        AppData.redisHost = ipEditText.text.trim().toString()
        AppData.redisPort = portEditText.text.trim().toString().toIntOrNull() ?: kotlin.run {
            AppData.showToast(this@MainActivity, "PORT 를 확인해주세요")
            idTextView.text = "PORT 를 확인해주세요"
            return
        }
        // view 에 값 세팅
        idTextView.text = "${AppData.ID} connecting..."
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
        if (channel == AppData.ID) binding.idTextView.text = "${AppData.ID} connected ! !"
        data?.apply {
            AppData.showToast(this@MainActivity, this)
            when {
                startsWith("already connected") -> return
                startsWith("check redis connection") -> return
                startsWith("successfully connected") -> checkConnection()

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
        if (AppData.redisHost.isBlank() || AppData.redisPort == 0) {
            AppData.showToast(this@MainActivity, "IP 와 PORT 를 확인해주세요")
            binding.idTextView.text = "IP 와 PORT 를 확인해주세요"
            return@with
        }
        putExtra(Extras.COMMAND, command)
        startForegroundService(this)
    }

    // 특정 channel 에 data 를 보내고 싶을 때
    private fun sendData(channel: String, data: String) = with(Intent(this, RedisService::class.java)) {
        putExtra(Extras.COMMAND, "send")
        putExtra(Extras.CHANNEL, channel)
        putExtra(Extras.DATA, data)
        startForegroundService(this)
    }

    // 매 CHECK_INTERVAL 마다 스스로에게 메시지 보내서 연결 확인 시작
    private fun checkConnection() = with(Intent(this, RedisService::class.java)) {
        putExtra(Extras.COMMAND, "connection-check")
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