package pt.ubi.di.pdm.finsafix.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        Secao::class, Equipamento::class, Pessoa::class, Avaria::class, EventoAvaria::class,
        Paragem::class, Parametro::class, Leitura::class, Producao::class, OrdemProducao::class, PecaUsada::class, Aviso::class, Autorizacao::class,
        Registo::class, Instrucao::class, Preventiva::class, Peca::class, AvariaTecnico::class
    ],
    version = 11,
    // O esquema de cada versão fica em app/schemas/ (no Git), para se ver a evolução das tabelas.
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun secaoDao(): SecaoDao
    abstract fun equipamentoDao(): EquipamentoDao
    abstract fun pessoaDao(): PessoaDao
    abstract fun avariaDao(): AvariaDao
    abstract fun eventoAvariaDao(): EventoAvariaDao
    abstract fun paragemDao(): ParagemDao
    abstract fun parametroDao(): ParametroDao
    abstract fun leituraDao(): LeituraDao
    abstract fun producaoDao(): ProducaoDao
    abstract fun ordemProducaoDao(): OrdemProducaoDao
    abstract fun pecaUsadaDao(): PecaUsadaDao
    abstract fun avisoDao(): AvisoDao
    abstract fun autorizacaoDao(): AutorizacaoDao
    abstract fun registoDao(): RegistoDao
    abstract fun instrucaoDao(): InstrucaoDao
    abstract fun preventivaDao(): PreventivaDao
    abstract fun pecaDao(): PecaDao
    abstract fun avariaTecnicoDao(): AvariaTecnicoDao

    companion object {
        // Uma só instância para toda a aplicação: abrir a base de dados é uma operação pesada.
        @Volatile
        private var instancia: AppDatabase? = null

        fun obter(context: Context): AppDatabase {
            return instancia ?: synchronized(this) {
                instancia ?: criar(context.applicationContext).also { instancia = it }
            }
        }

        private fun criar(context: Context): AppDatabase {
            return Room.databaseBuilder(context, AppDatabase::class.java, "finsafix.db")
                // Os dados são de demonstração e voltam a carregar-se: ao mudar o esquema apaga-se e recria-se a base de dados.
                .fallbackToDestructiveMigration(true)
                .addCallback(object : Callback() {
                    // Corre sempre que a base de dados abre (e não só ao criar o ficheiro): se a app foi fechada a meio
                    // do primeiro carregamento, a base de dados ficaria vazia para sempre.
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        CoroutineScope(Dispatchers.IO).launch {
                            CarregadorDadosDemo(context).carregarSeVazia(obter(context))
                        }
                    }
                })
                .build()
        }
    }
}
