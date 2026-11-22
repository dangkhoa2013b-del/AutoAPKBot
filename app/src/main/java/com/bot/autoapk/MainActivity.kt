package com.bot.autoapk

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val boardSize = 10
    private val winCount = 5
    private val board = Array(boardSize) { IntArray(boardSize) }
    private val cells = Array(boardSize) { arrayOfNulls<Button>(boardSize) }
    private var isPlayerX = true
    private var isGameOver = false

    private lateinit var tvStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#F5F5F5"))
            setPadding(24, 24, 24, 24)
        }

        val tvTitle = TextView(this).apply {
            text = "CỜ CARO 10x10"
            textSize = 24f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#212121"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 16)
        }
        rootLayout.addView(tvTitle)

        tvStatus = TextView(this).apply {
            text = "Lượt chơi: Người chơi X"
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#D32F2F"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 24)
        }
        rootLayout.addView(tvStatus)

        val gridLayout = GridLayout(this).apply {
            rowCount = boardSize
            columnCount = boardSize
            setBackgroundColor(Color.parseColor("#BDBDBD"))
            setPadding(4, 4, 4, 4)
        }

        val screenWidth = resources.displayMetrics.widthPixels
        val cellSize = (screenWidth - 64) / boardSize

        for (r in 0 until boardSize) {
            for (c in 0 until boardSize) {
                val btn = Button(this).apply {
                    text = ""
                    textSize = 16f
                    setTypeface(null, Typeface.BOLD)
                    setBackgroundColor(Color.WHITE)
                    setPadding(0, 0, 0, 0)
                    val params = GridLayout.LayoutParams().apply {
                        width = cellSize - 4
                        height = cellSize - 4
                        setMargins(2, 2, 2, 2)
                    }
                    layoutParams = params
                    setOnClickListener { onCellClicked(r, c) }
                }
                cells[r][c] = btn
                gridLayout.addView(btn)
            }
        }
        rootLayout.addView(gridLayout)

        val btnReset = Button(this).apply {
            text = "Chơi Lại Ván Mới"
            textSize = 16f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#1976D2"))
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 32
            }
            layoutParams = params
            setOnClickListener { resetGame() }
        }
        rootLayout.addView(btnReset)

        setContentView(rootLayout)
    }

    private fun onCellClicked(r: Int, c: Int) {
        if (isGameOver || board[r][c] != 0) return

        val currentPlayer = if (isPlayerX) 1 else 2
        board[r][c] = currentPlayer

        cells[r][c]?.apply {
            text = if (isPlayerX) "X" else "O"
            setTextColor(
                if (isPlayerX) Color.parseColor("#D32F2F")
                else Color.parseColor("#1976D2")
            )
        }

        val winCells = checkWin(r, c, currentPlayer)
        if (winCells != null) {
            isGameOver = true
            val winnerName = if (isPlayerX) "X" else "O"
            tvStatus.text = "CHÚC MỪNG! Người chơi $winnerName THẮNG!"
            tvStatus.setTextColor(Color.parseColor("#388E3C"))
            for ((wr, wc) in winCells) {
                cells[wr][wc]?.setBackgroundColor(Color.parseColor("#C8E6C9"))
            }
            return
        }

        if (isBoardFull()) {
            isGameOver = true
            tvStatus.text = "HÒA NHAU! Bàn cờ đã đầy."
            tvStatus.setTextColor(Color.parseColor("#F57C00"))
            return
        }

        isPlayerX = !isPlayerX
        if (isPlayerX) {
            tvStatus.text = "Lượt chơi: Người chơi X"
            tvStatus.setTextColor(Color.parseColor("#D32F2F"))
        } else {
            tvStatus.text = "Lượt chơi: Người chơi O"
            tvStatus.setTextColor(Color.parseColor("#1976D2"))
        }
    }

    private fun checkWin(row: Int, col: Int, player: Int): List<Pair<Int, Int>>? {
        val directions = arrayOf(
            intArrayOf(0, 1),
            intArrayOf(1, 0),
            intArrayOf(1, 1),
            intArrayOf(1, -1)
        )

        for (dir in directions) {
            val line = mutableListOf(Pair(row, col))
            var r = row + dir[0]
            var c = col + dir[1]
            while (r in 0 until boardSize && c in 0 until boardSize && board[r][c] == player) {
                line.add(Pair(r, c))
                r += dir[0]
                c += dir[1]
            }

            r = row - dir[0]
            c = col - dir[1]
            while (r in 0 until boardSize && c in 0 until boardSize && board[r][c] == player) {
                line.add(Pair(r, c))
                r -= dir[0]
                c -= dir[1]
            }

            if (line.size >= winCount) return line
        }
        return null
    }

    private fun isBoardFull(): Boolean {
        for (r in 0 until boardSize) {
            for (c in 0 until boardSize) {
                if (board[r][c] == 0) return false
            }
        }
        return true
    }

    private fun resetGame() {
        isPlayerX = true
        isGameOver = false
        tvStatus.text = "Lượt chơi: Người chơi X"
        tvStatus.setTextColor(Color.parseColor("#D32F2F"))
        for (r in 0 until boardSize) {
            for (c in 0 until boardSize) {
                board[r][c] = 0
                cells[r][c]?.apply {
                    text = ""
                    setBackgroundColor(Color.WHITE)
                }
            }
        }
    }
}
