import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.jeasy.rules.api.Action
import org.jeasy.rules.api.Condition
import org.jeasy.rules.api.Facts
import org.jeasy.rules.api.Rules
import org.jeasy.rules.core.DefaultRulesEngine
import org.jeasy.rules.core.RuleBuilder
import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode

fun main() {
    System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "debug")

    val mapper = ObjectMapper(YAMLFactory()).registerKotlinModule()
    val ruleset = mapper.readValue(File("src/main/resources/rules.yaml"), NwFeeRuleset::class.java)
    val nwFeeRules = createNwFeeRules(ruleset.fees)

    val sampleDebitTx = createSampleVisaDebitTx() // this will be the actual payment data
    val sampleCreditTx = createSampleVisaCreditTx() // this will be the actual payment data

    executeNwFeeRules(sampleDebitTx, nwFeeRules)
    executeNwFeeRules(sampleCreditTx, nwFeeRules)

    println("Debit NW fee: ${formatNwFee(sampleDebitTx.nwFee)}")
    println("Credit NW fee: ${formatNwFee(sampleCreditTx.nwFee)}")
}

private fun createNwFeeRules(fees: List<NwFee>) = Rules(
    fees.map { fee ->
        RuleBuilder()
            .name(fee.key)
            // creates the nw fee rule criteria
            .`when`(GenericNwFeeRule(fee.rules))
            // creates an action to execute if the rule applies using the fee values
            .then(NwFeeCalculationAction(pctRate = fee.pctRate, authRate = fee.authRate, txRate = fee.txRate))
            .build()
    }.toSet()
)

private fun executeNwFeeRules(sampleTx: TxData, rules: Rules) {
    val facts = Facts()
    facts.put("tx", sampleTx)

    val rulesEngine = DefaultRulesEngine()
    rulesEngine.fire(rules, facts)
}

fun createSampleVisaDebitTx() = TxData(
    orderAmount = BigDecimal.TEN,
    cardType = "VISA",
    cardEntryMode = "SWIPED",
    debit = true,
    optBlue = false,
    international = false,
    prepaid = false
)

fun createSampleVisaCreditTx() = TxData(
    orderAmount = BigDecimal.TEN,
    cardType = "VISA",
    cardEntryMode = "SWIPED",
    debit = false,
    optBlue = false,
    international = false,
    prepaid = false
)

class NwFeeCalculationAction(
    private val authRate: BigDecimal,
    private val txRate: BigDecimal,
    private val pctRate: BigDecimal
) : Action {
    override fun execute(facts: Facts) {
        val tx: TxData = facts.get("tx")

        val authCount = when (tx.cardEntryMode) {
            "TOKENIZED", "PRE_AUTHED" -> 3
            "INCREMENTAL_PRE_AUTHED" -> 2
            else -> 1
        }

        tx.nwFee += pctRate * tx.orderAmount + txRate + authRate * authCount.toBigDecimal()
    }
}

class GenericNwFeeRule(private val rules: List<NwFeeRule>) : Condition {
    override fun evaluate(facts: Facts): Boolean {
        val tx: TxData = facts.get("tx")

        rules.forEach { r ->
            val ruleResult = tx.avs.eq(r.avs)
                    && tx.cvc.eq(r.cvc)
                    && tx.debit.eq(r.debit)
                    && tx.refund.eq(r.refund)
                    && tx.optBlue.eq(r.optBlue)
                    && tx.international.eq(r.international)
                    && tx.prepaid.eq(r.prepaid)
                    && tx.cardEntryMode.eq(r.cardEntryMode)
                    && tx.cardType.eq(r.cardType)
                    && tx.orderAmount.ge(r.minTxAmount)
                    && tx.orderAmount.le(r.maxTxAmount)

            if (ruleResult)
                return true
        }

        return false
    }
}

private fun <T> Comparable<T>.eq(value: T?) = value == null || value == this
private fun BigDecimal.ge(value: BigDecimal?) = value == null || this >= value
private fun BigDecimal.le(value: BigDecimal?) = value == null || this <= value
fun formatNwFee(value: BigDecimal): BigDecimal = value.setScale(4, RoundingMode.HALF_EVEN)

data class NwFeeRuleset(
    val id: String,
    val name: String,
    val effectiveDate: String,
    val status: String,
    val fees: List<NwFee>
)

data class NwFee(
    val key: String,
    val name: String,
    val description: String,
    val rules: List<NwFeeRule>,
    val authRate: BigDecimal = BigDecimal.ZERO,
    val txRate: BigDecimal = BigDecimal.ZERO,
    val pctRate: BigDecimal = BigDecimal.ZERO
)

data class NwFeeRule(
    val minTxAmount: BigDecimal? = null,
    val maxTxAmount: BigDecimal? = null,
    val cardType: String? = null,
    val cardEntryMode: String? = null,
    val debit: Boolean? = null,
    val prepaid: Boolean? = null,
    val international: Boolean? = null,
    val optBlue: Boolean? = null,
    val refund: Boolean? = null,
    val avs: Boolean? = null,
    val cvc: Boolean? = null
)

data class TxData(
    val orderAmount: BigDecimal,
    val cardType: String,
    val cardEntryMode: String,
    val debit: Boolean,
    val prepaid: Boolean,
    val international: Boolean,
    val optBlue: Boolean,
    val refund: Boolean = false,
    val avs: Boolean = false,
    val cvc: Boolean = false,
    var nwFee: BigDecimal = BigDecimal.ZERO
)