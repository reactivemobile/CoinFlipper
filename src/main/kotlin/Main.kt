import io.ktor.application.*
import io.ktor.features.CallLogging
import io.ktor.features.ContentNegotiation
import io.ktor.gson.gson
import io.ktor.pipeline.PipelineContext
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils.create
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.event.Level
import java.security.SecureRandom

fun main(args: Array<String>) {
    val secureRandom = SecureRandom()
    val booleanRandomiser = { secureRandom.nextBoolean() }

    val server = embeddedServer(Netty, port = 8080) {

        install(ContentNegotiation) {
            gson {
                setPrettyPrinting()
            }
        }

        install(CallLogging) {
            level = Level.TRACE
        }

        routing {
            get("/flip") {
                handleRootRequest(booleanRandomiser)
            }

            get("/outcomes") {
                handleGetOutcomesRequest()
            }
        }
    }

    Database.connect("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1", driver = "org.h2.Driver")
    transaction { create(RESULTS) }
    server.start(wait = true)
}

suspend fun PipelineContext<Unit, ApplicationCall>.handleGetOutcomesRequest() {
    var allOutcomes: List<Coin> = ArrayList()
    transaction {
        allOutcomes = RESULTS.selectAll().map { resultRow -> Coin(Face.valueOf(resultRow[RESULTS.face].toString())) }
    }.apply {
        call.respond(allOutcomes)
    }
}

private suspend fun PipelineContext<Unit, ApplicationCall>.handleRootRequest(booleanRandomiser: () -> Boolean) {
    val result = booleanRandomiser.invoke()
    val faceValue: Face = if (result) Face.HEADS else Face.TAILS
    transaction {
        RESULTS.insert { it[face] = faceValue.name }
    }
    call.respond(Coin(faceValue))
}

data class Coin(var face: Face)

enum class Face { HEADS, TAILS }

object RESULTS : org.jetbrains.exposed.sql.Table() {
    val id = integer("id").primaryKey().autoIncrement()
    val face = varchar("face", 5)
}
