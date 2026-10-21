package com.qin.f21.toolbox.ui

import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.qin.f21.toolbox.R
import com.qin.f21.toolbox.Toolbox
import com.qin.f21.toolbox.core.wallet.Money
import com.qin.f21.toolbox.core.wallet.WalletAccount
import com.qin.f21.toolbox.core.wallet.WalletEntry
import com.qin.f21.toolbox.core.wallet.WalletEntryKind
import com.qin.f21.toolbox.core.wallet.WalletLedger
import com.qin.f21.toolbox.core.wallet.RateSource
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The wallet: one main balance, any number of cash pockets in their own currencies, and
 * one "add" button that pops the three movements (expense, top up, withdraw) out of itself.
 *
 * Everything shown here is derived from the local ledger in `core/wallet` - balances are
 * replayed from the recorded movements, and the combined total converts every cash pocket
 * that has not been separated from the main balance with the bundled offline rates.
 */
class WalletScreen(host: ScreenHost) : Screen(host) {

    override val title: String = "Wallet"
    override val icon: String? = "ic_wallet"

    private lateinit var column: LinearLayout
    private lateinit var speedDial: SpeedDial
    private val dateFormat = SimpleDateFormat("d MMM, HH:mm", Locale.US)

    override fun onCreateView(parent: ViewGroup): View {
        val context = host.activity
        val root = FrameLayout(context)
        root.setBackgroundColor(Widgets.color(context, R.color.bg))

        val scroll = ScrollView(context).forKeypad()
        scroll.isFillViewport = true
        column = Widgets.column(context)
        column.setPadding(
            Widgets.dp(context, 10), Widgets.dp(context, 6),
            Widgets.dp(context, 10), Widgets.dp(context, 92)
        )
        scroll.addView(column, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        root.addView(scroll, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))

        speedDial = SpeedDial(
            context,
            listOf(
                SpeedDial.Action(
                    context.getString(R.string.wallet_expense),
                    R.drawable.ic_minus,
                    Widgets.color(context, R.color.danger_dim)
                ) { host.push(WalletEntryScreen(host, WalletEntryKind.EXPENSE)) },
                SpeedDial.Action(
                    context.getString(R.string.wallet_top_up),
                    R.drawable.ic_plus,
                    Widgets.color(context, R.color.primary_dim)
                ) { host.push(WalletEntryScreen(host, WalletEntryKind.TOP_UP)) },
                SpeedDial.Action(
                    context.getString(R.string.wallet_withdraw),
                    R.drawable.ic_download,
                    Widgets.color(context, R.color.accent_dim)
                ) { host.push(WalletEntryScreen(host, WalletEntryKind.WITHDRAW)) }
            )
        )
        root.addView(speedDial.root)
        refresh()
        return root
    }

    override fun onShown() {
        refresh()
    }

    /** Back closes the pop-out menu before it leaves the screen. */
    override fun onBackPressed(): Boolean {
        if (speedDial.isExpanded) {
            speedDial.collapse()
            return true
        }
        return false
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        // '*' on the physical keypad opens the add menu wherever the focus is
        if (keyCode == HardwareKeys.KEY_STAR) {
            speedDial.toggle()
            return true
        }
        return false
    }

    // ---- content --------------------------------------------------------------

    private fun refresh() {
        val context = host.activity
        Toolbox.wallet.ensureMainAccount(
            context.getString(R.string.wallet_main_name),
            Toolbox.currency.currencies,
            Toolbox.currency.base
        )
        val ledger = Toolbox.wallet.ledger
        val rates = Toolbox.rates()
        val main = ledger.mainAccount
        val mainCurrency = main?.currency ?: Toolbox.currency.base

        column.removeAllViews()
        host.setTitle(title, main?.let { Money.format(ledger.balanceOf(it.id), it.currency) })

        if (main != null) {
            column.addView(mainCard(main, ledger.balanceOf(main.id)))
        }

        column.addView(totalCard(ledger, mainCurrency))

        column.addView(Widgets.sectionHeader(context, context.getString(R.string.wallet_cash_accounts)))
        if (ledger.cashAccounts.isEmpty()) {
            column.addView(hint(context.getString(R.string.wallet_no_cash)))
        }
        for (account in ledger.cashAccounts) {
            column.addView(cashRow(account, ledger, rates, mainCurrency))
        }
        column.addView(addAccountRow())

        column.addView(Widgets.sectionHeader(context, context.getString(R.string.wallet_activity)))
        val entries = ledger.entries.take(MAX_SHOWN_ENTRIES)
        if (entries.isEmpty()) {
            column.addView(hint(context.getString(R.string.wallet_no_entries)))
        }
        for (entry in entries) {
            column.addView(entryRow(entry, ledger))
        }
    }

    private fun mainCard(account: WalletAccount, balance: Double): View {
        val context = host.activity
        val card = Widgets.column(context)
        card.setPadding(
            Widgets.dp(context, 12), Widgets.dp(context, 10),
            Widgets.dp(context, 12), Widgets.dp(context, 10)
        )
        card.background = Widgets.focusBackground(
            context,
            Widgets.color(context, R.color.primary_dim),
            Widgets.color(context, R.color.primary)
        )
        card.isClickable = true
        card.isFocusable = true

        val caption = Widgets.text(context, context.getString(R.string.wallet_main_balance), R.style.Text_Label)
        caption.setTextColor(Widgets.color(context, R.color.text_secondary))
        card.addView(caption)

        val amount = Widgets.text(context, Money.format(balance, account.currency), R.style.Text_Display)
        amount.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 26f)
        amount.maxLines = 1
        card.addView(amount)

        card.setOnClickListener { host.push(WalletAccountScreen(host, account.id)) }
        return card
    }

    private fun totalCard(
        ledger: WalletLedger,
        mainCurrency: String
    ): View {
        val context = host.activity
        val card = Widgets.column(context)
        card.setPadding(
            Widgets.dp(context, 12), Widgets.dp(context, 8),
            Widgets.dp(context, 12), Widgets.dp(context, 8)
        )
        card.background = Widgets.roundedBackground(
            context,
            Widgets.color(context, R.color.surface),
            Widgets.color(context, R.color.outline)
        )
        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.topMargin = Widgets.dp(context, 6)
        card.layoutParams = params

        val row = Widgets.row(context)
        row.gravity = Gravity.CENTER_VERTICAL
        val label = Widgets.text(context, context.getString(R.string.wallet_total), R.style.Text_Label)
        label.setTextColor(Widgets.color(context, R.color.text_secondary))
        row.addView(label, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val value = Widgets.text(
            context,
            Money.format(ledger.totalIn(mainCurrency, Toolbox.rates()), mainCurrency),
            R.style.Text_Subtitle
        )
        value.setTextColor(Widgets.color(context, R.color.text_primary))
        row.addView(value)
        card.addView(row)

        val included = ledger.cashAccounts.count { it.includedInTotal }
        val separate = ledger.separateAccounts().size
        val note = when {
            ledger.cashAccounts.isEmpty() -> context.getString(R.string.wallet_total_no_cash)
            separate == 0 -> context.getString(R.string.wallet_total_note_all, included)
            else -> context.getString(
                R.string.wallet_total_note,
                included,
                ledger.cashAccounts.size,
                separate
            )
        }
        card.addView(Widgets.caption(context, note))
        return card
    }

    private fun cashRow(
        account: WalletAccount,
        ledger: WalletLedger,
        rates: RateSource,
        mainCurrency: String
    ): View {
        val context = host.activity
        val row = Widgets.row(context)
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(
            Widgets.dp(context, 10), Widgets.dp(context, 8),
            Widgets.dp(context, 10), Widgets.dp(context, 8)
        )
        row.background = Widgets.focusBackground(
            context,
            Widgets.color(context, R.color.surface),
            Widgets.color(context, R.color.outline)
        )
        row.isClickable = true
        row.isFocusable = true

        val texts = Widgets.column(context)
        val name = Widgets.text(context, account.name, R.style.Text_Body)
        name.maxLines = 1
        name.ellipsize = android.text.TextUtils.TruncateAt.END
        texts.addView(name)

        val balance = ledger.balanceOf(account.id)
        val detail = if (account.currency == mainCurrency) {
            Money.format(balance, account.currency)
        } else {
            context.getString(
                R.string.wallet_converted_line,
                Money.format(balance, account.currency),
                Money.format(rates.convert(balance, account.currency, mainCurrency), mainCurrency)
            )
        }
        texts.addView(Widgets.caption(context, detail))
        if (!account.includedInTotal) {
            texts.addView(Widgets.caption(context, context.getString(R.string.wallet_separate_badge)))
        }
        row.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val chevron = ImageView(context)
        chevron.setImageResource(R.drawable.ic_back)
        chevron.setColorFilter(Widgets.color(context, R.color.text_muted))
        chevron.rotation = 180f
        chevron.scaleX = -1f
        row.addView(chevron, LinearLayout.LayoutParams(
            Widgets.dp(context, 16), Widgets.dp(context, 16)
        ))

        row.setOnClickListener { host.push(WalletAccountScreen(host, account.id)) }
        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.bottomMargin = Widgets.dp(context, 6)
        row.layoutParams = params
        return row
    }

    private fun addAccountRow(): View {
        val context = host.activity
        val row = Widgets.row(context)
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(
            Widgets.dp(context, 10), Widgets.dp(context, 10),
            Widgets.dp(context, 10), Widgets.dp(context, 10)
        )
        row.background = Widgets.focusBackground(
            context,
            Widgets.color(context, R.color.surface_alt),
            Widgets.color(context, R.color.outline)
        )
        row.isClickable = true
        row.isFocusable = true

        val icon = ImageView(context)
        icon.setImageResource(R.drawable.ic_plus)
        icon.setColorFilter(Widgets.color(context, R.color.primary))
        row.addView(icon, LinearLayout.LayoutParams(
            Widgets.dp(context, 18), Widgets.dp(context, 18)
        ))
        row.addView(Widgets.spacer(context, 8))
        val label = Widgets.text(context, context.getString(R.string.wallet_add_account), R.style.Text_Body)
        row.addView(label, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.setOnClickListener { host.push(WalletAccountScreen(host, null)) }

        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.bottomMargin = Widgets.dp(context, 6)
        row.layoutParams = params
        return row
    }

    private fun entryRow(entry: WalletEntry, ledger: WalletLedger): View {
        val context = host.activity
        val account = ledger.account(entry.accountId)
        val row = Widgets.row(context)
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(
            Widgets.dp(context, 10), Widgets.dp(context, 7),
            Widgets.dp(context, 10), Widgets.dp(context, 7)
        )
        row.background = Widgets.roundedBackground(
            context,
            Widgets.color(context, R.color.surface),
            Widgets.color(context, R.color.outline)
        )

        val icon = ImageView(context)
        icon.setImageResource(iconOf(entry.kind))
        icon.setColorFilter(Widgets.color(context, tintOf(entry.kind)))
        row.addView(icon, LinearLayout.LayoutParams(
            Widgets.dp(context, 18), Widgets.dp(context, 18)
        ))
        row.addView(Widgets.spacer(context, 8))

        val texts = Widgets.column(context)
        texts.addView(Widgets.text(context, headline(entry, account?.name), R.style.Text_Body))
        val caption = caption(entry, account, ledger)
        if (caption.isNotEmpty()) texts.addView(Widgets.caption(context, caption))
        row.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val sign = if (entry.kind == WalletEntryKind.EXPENSE) "-" else "+"
        val amount = Widgets.text(
            context,
            sign + Money.format(entry.amount, account?.currency ?: ""),
            R.style.Text_Body
        )
        amount.setTextColor(Widgets.color(context, tintOf(entry.kind)))
        amount.gravity = Gravity.END
        row.addView(amount)

        val trash = ImageView(context)
        trash.setImageResource(R.drawable.ic_trash)
        trash.setColorFilter(Widgets.color(context, R.color.text_muted))
        trash.setPadding(
            Widgets.dp(context, 6), Widgets.dp(context, 6),
            0, Widgets.dp(context, 6)
        )
        trash.isClickable = true
        trash.isFocusable = true
        trash.contentDescription = context.getString(R.string.wallet_delete_entry)
        trash.setOnClickListener { confirmDelete(entry) }
        row.addView(trash, LinearLayout.LayoutParams(
            Widgets.dp(context, 26), Widgets.dp(context, 26)
        ))

        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.bottomMargin = Widgets.dp(context, 6)
        row.layoutParams = params
        return row
    }

    private fun iconOf(kind: WalletEntryKind): Int = when (kind) {
        WalletEntryKind.TOP_UP -> R.drawable.ic_plus
        WalletEntryKind.EXPENSE -> R.drawable.ic_minus
        WalletEntryKind.WITHDRAW -> R.drawable.ic_download
    }

    private fun tintOf(kind: WalletEntryKind): Int = when (kind) {
        WalletEntryKind.TOP_UP -> R.color.success
        WalletEntryKind.EXPENSE -> R.color.danger
        WalletEntryKind.WITHDRAW -> R.color.accent
    }

    private fun headline(entry: WalletEntry, accountName: String?): String {
        val context = host.activity
        val name = accountName ?: context.getString(R.string.wallet_unknown_account)
        return when (entry.kind) {
            WalletEntryKind.TOP_UP -> context.getString(R.string.wallet_headline_top_up, name)
            WalletEntryKind.EXPENSE -> context.getString(R.string.wallet_headline_expense, name)
            WalletEntryKind.WITHDRAW -> context.getString(R.string.wallet_headline_withdraw, name)
        }
    }

    private fun caption(
        entry: WalletEntry,
        account: WalletAccount?,
        ledger: WalletLedger
    ): String {
        val context = host.activity
        val parts = ArrayList<String>()
        if (entry.timestamp > 0L) parts.add(dateFormat.format(Date(entry.timestamp)))
        if (entry.note.isNotEmpty()) parts.add(entry.note)
        // a local copy: `counterAmount` comes from another module, so the compiler will
        // not smart cast the nullable property itself
        val counter = entry.counterAmount
        if (entry.kind == WalletEntryKind.WITHDRAW && counter != null && account != null) {
            val main = ledger.mainAccount
            parts.add(
                context.getString(
                    R.string.wallet_withdraw_detail,
                    Money.format(counter, main?.currency ?: ""),
                    Money.format(entry.rate ?: 1.0, "", 4)
                )
            )
        }
        return parts.joinToString(" · ")
    }

    private fun hint(message: String): View {
        val context = host.activity
        val view = Widgets.caption(context, message)
        view.setPadding(0, Widgets.dp(context, 4), 0, Widgets.dp(context, 10))
        return view
    }

    private fun confirmDelete(entry: WalletEntry) {
        val context = host.activity
        android.app.AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.wallet_delete_entry))
            .setMessage(context.getString(R.string.wallet_delete_confirm))
            .setNegativeButton(context.getString(R.string.no), null)
            .setPositiveButton(context.getString(R.string.yes)) { _, _ ->
                val entries = Toolbox.wallet.ledger.entries.filter { it.id != entry.id }
                Toolbox.wallet.update(
                    WalletLedger(Toolbox.wallet.ledger.accounts, entries)
                )
                host.showMessage(context.getString(R.string.wallet_entry_deleted))
                refresh()
            }
            .show()
    }

    companion object {
        private const val MAX_SHOWN_ENTRIES = 30
    }
}
