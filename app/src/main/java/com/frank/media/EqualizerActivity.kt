package com.frank.media

import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.util.Pair
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.frank.media.adapter.EqualizerAdapter
import com.frank.media.listener.OnSeeBarListener
import java.lang.StringBuilder
import java.util.ArrayList

class EqualizerActivity : AppCompatActivity(), OnSeeBarListener {

    private val bandsList = intArrayOf(
            65, 92, 131, 185, 262, 370,
            523, 740, 1047, 1480, 2093, 2960,
            4180, 5920, 8372, 11840, 16744, 20000)

    private val selectBandList = IntArray(bandsList.size)
    private val minEQLevel = 0
    private var filterThread: Thread? = null
    private var mAudioPlayer: MediaJniHelper? = null
    private var equalizerAdapter: EqualizerAdapter? = null
    private var audioPath = Environment.getExternalStorageDirectory().path + "/tiger.mp3"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_equalizer)
        initView()
        setupEqualizer()
        doEqualize()
    }

    private fun initView() {
        val equalizerView = findViewById<RecyclerView>(R.id.list_equalizer)
        val layoutManager = LinearLayoutManager(this)
        layoutManager.orientation = RecyclerView.VERTICAL
        equalizerView.layoutManager = layoutManager
        equalizerAdapter = EqualizerAdapter(this, this)
        equalizerView.adapter = equalizerAdapter
    }

    private fun setupEqualizer() {
        val equalizerList = ArrayList<Pair<*, *>>()
        val maxEQLevel = 20
        for (element in bandsList) {
            val centerFreq = "$element Hz"
            val pair = Pair.create(centerFreq, 0)
            equalizerList.add(pair)
        }
        if (equalizerAdapter != null) {
            equalizerAdapter!!.setMaxProgress(maxEQLevel - minEQLevel)
            equalizerAdapter!!.setEqualizerList(equalizerList)
        }
        mAudioPlayer = MediaJniHelper()
    }

    private fun doEqualize() {
        doEqualize(0, 0)
    }

    private fun doEqualize(index: Int, progress: Int) {
        if (filterThread == null) {
            val filter = "superequalizer=6b=4:8b=5:10b=5"
            filterThread = Thread(Runnable {
                mAudioPlayer!!.init()
                mAudioPlayer!!.playAudio(audioPath)
            })
            filterThread!!.start()
        } else {
            if (index < 0 || index >= selectBandList.size) return
            selectBandList[index] = progress
            val builder = StringBuilder()
            builder.append("superequalizer=")
            for (i in selectBandList.indices) {
                if (selectBandList[i] > 0) {
                    builder.append(i + 1).append("b=").append(selectBandList[i]).append(":")
                }
            }
            builder.deleteCharAt(builder.length - 1)
            Log.e("Equalizer", "update filter=$builder")
            mAudioPlayer!!.filterAgain(builder.toString())
        }
    }

    override fun onProgress(index: Int, progress: Int) {
        doEqualize(index, progress)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (filterThread != null) {
            mAudioPlayer!!.release()
            filterThread?.interrupt()
            filterThread = null
        }
    }
}
