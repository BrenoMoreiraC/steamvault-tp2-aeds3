package tp1.app;

import java.io.File;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import tp1.arquivo.ArquivoIndexado;
import tp1.arquivo.ArquivoSequencial;
import tp1.arquivo.CargaCSV;
import tp1.modelo.Jogo;
import tp1.ordenacao.ChaveOrdenacao;
import tp1.ordenacao.OrdenacaoExterna;
import tp1.util.Terminal;

/**
 * SteamVault — TP2 de AEDS III
 *
 * CRUD com apoio de índices: Árvore B+, Hashing Estendido e Listas Invertidas.
 */
public class Main {

    /** Caminho padrão do arquivo binário de dados. */
    private static final String CAMINHO_DB = "dados/jogos.db";
    /** Caminho padrão do CSV de carga. */
    private static final String CAMINHO_CSV = "dados/steam.csv";
    /** Quantos registros são mostrados por página na listagem. */
    private static final int TAMANHO_PAGINA = 15;

    /** Gerenciador indexado aberto durante toda a sessão. */
    private static ArquivoIndexado arquivoIndexado;

    public static void main(String[] args) {
        try {
            arquivoIndexado = new ArquivoIndexado(CAMINHO_DB);
            apresentacao();
            lacoPrincipal();
        } catch (Exception e) {
            Terminal.erro("Falha inesperada: " + e.getMessage());
            e.printStackTrace(Terminal.SAIDA);
        } finally {
            try { if (arquivoIndexado != null) arquivoIndexado.close(); } catch (Exception ignorado) { }
        }
    }

    // ------------------------------------------------------------ interface

    private static void apresentacao() {
        Terminal.imprimir("");
        Terminal.linhaDupla();
        Terminal.imprimir("   ____  _                     __     __          _ _   ");
        Terminal.imprimir("  / ___|| |_ ___  __ _ _ __ _\\ \\   / /_ _ _   _| | |_ ");
        Terminal.imprimir("  \\___ \\| __/ _ \\/ _` | '_ ` _ \\ \\ / / _` | | | | | __|");
        Terminal.imprimir("   ___) | ||  __/ (_| | | | | | \\ V / (_| | |_| | | |_ ");
        Terminal.imprimir("  |____/ \\__\\___|\\__,_|_| |_| |_|\\_/ \\__,_|\\__,_|_|\\__|");
        Terminal.imprimir("");
        Terminal.imprimir("  AEDS III - TP2 | CRUD Indexado (Árvore B+, Hash Estendido, Listas Invertidas)");
        Terminal.linhaDupla();
    }

    private static void lacoPrincipal() throws Exception {
        boolean executando = true;
        while (executando) {
            Terminal.titulo("menu principal - tp2");
            Terminal.imprimir("  BASE DE DADOS");
            Terminal.imprimir("   1 - Carregar base a partir de um arquivo CSV");
            Terminal.imprimir("   2 - Listar registros (paginado)");
            Terminal.imprimir("   3 - Reconstruir/Atualizar Índices (B+, Hash, Listas)");
            Terminal.imprimir("");
            Terminal.imprimir("  CRUD COM APOIO DOS ÍNDICES");
            Terminal.imprimir("   4 - Buscar por ID (Árvore B+ ou Hash Estendido)");
            Terminal.imprimir("   5 - Buscar por Listas Invertidas (Gênero / Dev / Composta)");
            Terminal.imprimir("   6 - Incluir um novo jogo");
            Terminal.imprimir("   7 - Atualizar um jogo (via B+ ou Hash)");
            Terminal.imprimir("   8 - Excluir um jogo (via B+ ou Hash)");
            Terminal.imprimir("");
            Terminal.imprimir("  OPERAÇÕES DO TP1 (LEGADO)");
            Terminal.imprimir("   9 - Estatísticas do arquivo binário");
            Terminal.imprimir("  10 - Ordenação externa (intercalação balanceada)");
            Terminal.imprimir("");
            Terminal.imprimir("   0 - Sair");
            Terminal.linha();

            int opcao = Terminal.lerInteiro("Opção", 0, 10);
            switch (opcao) {
                case 1 -> carregarCSV();
                case 2 -> listar();
                case 3 -> reconstruirIndices();
                case 4 -> buscarPorIdIndexado();
                case 5 -> buscarPorListasInvertidas();
                case 6 -> incluir();
                case 7 -> atualizar();
                case 8 -> excluir();
                case 9 -> estatisticas();
                case 10 -> ordenacaoExterna();
                case 0 -> executando = false;
            }
        }
        Terminal.imprimir("\n  Encerrado. Base salva em: " + new File(CAMINHO_DB).getAbsolutePath() + "\n");
    }

    // ------------------------------------------------------- 1. carga do CSV

    private static void carregarCSV() throws Exception {
        Terminal.titulo("carga da base de dados");
        Terminal.imprimir("  Informe o caminho do CSV ou apenas ENTER para usar o arquivo padrão.");
        String caminho = Terminal.lerTextoOuManter("Arquivo CSV", CAMINHO_CSV);

        File f = new File(caminho);
        if (!f.exists()) {
            Terminal.erro("Arquivo não encontrado: " + f.getAbsolutePath());
            Terminal.pausar();
            return;
        }

        boolean limpar = true;
        if (arquivoIndexado.getArquivoDados().estatisticas().validos > 0) {
            Terminal.aviso("Já existem registros na base.");
            limpar = Terminal.lerSimNao("Apagar a base atual antes de importar?", true);
        }

        Terminal.imprimir("\n  Importando...");
        CargaCSV.Resultado r = CargaCSV.importar(caminho, arquivoIndexado.getArquivoDados(), limpar,
                n -> Terminal.imprimir("          " + n + " registros..."));

        Terminal.linha();
        Terminal.sucesso(r.importados + " registros importados em " + r.milissegundos + " ms.");
        
        Terminal.imprimir("  Indexando novos registros nas estruturas do TP2...");
        int indexados = arquivoIndexado.reconstruirIndices();
        Terminal.sucesso(indexados + " registros indexados na Árvore B+, Hash e Listas Invertidas.");
        Terminal.pausar();
    }

    // ---------------------------------------------------------- 2. listagem

    private static void listar() throws Exception {
        int pagina = 0;
        while (true) {
            List<Jogo> jogos = arquivoIndexado.getArquivoDados().listar(pagina * TAMANHO_PAGINA, TAMANHO_PAGINA);
            Terminal.titulo("registros (página " + (pagina + 1) + ")");

            if (jogos.isEmpty()) {
                if (pagina == 0) {
                    Terminal.aviso("A base está vazia. Use a opção 1 para carregar o CSV.");
                    Terminal.pausar();
                    return;
                }
                Terminal.aviso("Não há mais registros.");
                pagina--;
            } else {
                Terminal.cabecalhoTabela();
                for (Jogo j : jogos) Terminal.imprimir(j.paraLinha());
            }

            Terminal.linha();
            Terminal.imprimir("  [P] próxima página   [A] página anterior   [V] voltar ao menu");
            String cmd = Terminal.lerTexto("Comando").toLowerCase();
            if (cmd.startsWith("p")) pagina++;
            else if (cmd.startsWith("a")) pagina = Math.max(0, pagina - 1);
            else return;
        }
    }

    // -------------------------------------------------- 3. reconstruir índices

    private static void reconstruirIndices() throws Exception {
        Terminal.titulo("reconstrução de índices");
        Terminal.imprimir("  Varrendo base sequencial e populando B+, Hash e Listas Invertidas...");
        long inicio = System.nanoTime();
        int qtd = arquivoIndexado.reconstruirIndices();
        double ms = (System.nanoTime() - inicio) / 1_000_000.0;
        Terminal.sucesso(qtd + " registros sincronizados nos índices em " + String.format("%.2f ms", ms));
        Terminal.pausar();
    }

    // -------------------------------------------------- 4. busca por id indexado

    private static void buscarPorIdIndexado() throws Exception {
        Terminal.titulo("busca por id com apoio de índice");
        int id = Terminal.lerInteiro("ID do jogo", 1, Integer.MAX_VALUE);

        Terminal.imprimir("  Qual estrutura de índice deseja usar?");
        Terminal.imprimir("   1 - Árvore B+ [O(log n)]");
        Terminal.imprimir("   2 - Hash Estendido [O(1) médio]");
        int escolha = Terminal.lerInteiro("Escolha", 1, 2);

        long inicio = System.nanoTime();
        Jogo jogo = (escolha == 1) ? arquivoIndexado.readComArvoreB(id) : arquivoIndexado.readComHash(id);
        double ms = (System.nanoTime() - inicio) / 1_000_000.0;

        if (jogo == null) {
            Terminal.erro("Registro não encontrado no índice.");
        } else {
            Terminal.linha();
            Terminal.imprimir(jogo.toString());
            Terminal.sucesso(String.format("Busca via %s concluída em %.4f ms!", 
                    (escolha == 1 ? "Árvore B+" : "Hash Estendido"), ms));
        }
        Terminal.pausar();
    }

    // ------------------------------------------------- 5. listas invertidas

    private static void buscarPorListasInvertidas() throws Exception {
        Terminal.titulo("busca por listas invertidas");
        Terminal.imprimir("  Opções de pesquisa:");
        Terminal.imprimir("   1 - Por Gênero (Lista Invertida 1)");
        Terminal.imprimir("   2 - Por Desenvolvedora (Lista Invertida 2)");
        Terminal.imprimir("   3 - Pesquisa Composta INTERSEÇÃO (Gênero E Desenvolvedora)");
        Terminal.imprimir("   4 - Pesquisa Composta UNIÃO (Gênero OU Desenvolvedora)");
        int modo = Terminal.lerInteiro("Opção", 1, 4);

        List<Jogo> resultados = new ArrayList<>();
        long inicio = System.nanoTime();

        switch (modo) {
            case 1 -> {
                String gen = Terminal.lerTextoObrigatorio("Gênero");
                inicio = System.nanoTime();
                resultados = arquivoIndexado.buscarPorGenero(gen);
            }
            case 2 -> {
                String dev = Terminal.lerTextoObrigatorio("Desenvolvedora");
                inicio = System.nanoTime();
                resultados = arquivoIndexado.buscarPorDesenvolvedora(dev);
            }
            case 3 -> {
                String gen = Terminal.lerTextoObrigatorio("Gênero");
                String dev = Terminal.lerTextoObrigatorio("Desenvolvedora");
                inicio = System.nanoTime();
                resultados = arquivoIndexado.buscarCompostaIntersecao(gen, dev);
            }
            case 4 -> {
                String gen = Terminal.lerTextoObrigatorio("Gênero");
                String dev = Terminal.lerTextoObrigatorio("Desenvolvedora");
                inicio = System.nanoTime();
                resultados = arquivoIndexado.buscarCompostaUniao(gen, dev);
            }
        }
        double ms = (System.nanoTime() - inicio) / 1_000_000.0;

        Terminal.linha();
        if (resultados.isEmpty()) {
            Terminal.aviso("Nenhum jogo encontrado com os critérios fornecidos.");
        } else {
            Terminal.cabecalhoTabela();
            for (Jogo j : resultados) Terminal.imprimir(j.paraLinha());
            Terminal.sucesso(String.format("%d jogo(s) encontrado(s) em %.2f ms.", resultados.size(), ms));
        }
        Terminal.pausar();
    }

    // --------------------------------------------------------- 6. incluir

    private static void incluir() throws Exception {
        Terminal.titulo("incluir novo jogo (sincronização de índices)");

        Jogo jogo = new Jogo();
        jogo.setNome(Terminal.lerTextoObrigatorio("Nome"));
        jogo.setDesenvolvedora(Terminal.lerTextoOuManter("Desenvolvedora", "Indefinida"));
        jogo.setFaixaJogadores(Terminal.lerTextoOuManter("Faixa de jogadores (até 8 caracteres)", "<20K"));
        jogo.setDataLancamento(Terminal.lerDataOuManter("Lançamento", LocalDate.now()));
        jogo.setGeneros(Terminal.lerListaOuManter("Gêneros", new String[]{"Indie"}));
        jogo.setPreco(Terminal.lerFloatOuManter("Preço (US$)", 0f));
        jogo.setAvaliacoesPositivas(Terminal.lerInteiroNaoNegativoOuManter("Avaliações positivas", 0));
        jogo.setAvaliacoesNegativas(Terminal.lerInteiroNaoNegativoOuManter("Avaliações negativas", 0));

        int id = arquivoIndexado.create(jogo);
        Terminal.linha();
        Terminal.sucesso("Registro criado com o ID " + id + " e sincronizado na Árvore B+, Hash e Listas Invertidas.");
        Terminal.pausar();
    }

    // -------------------------------------------------------- 7. atualizar

    private static void atualizar() throws Exception {
        Terminal.titulo("atualizar jogo com apoio de índice");
        int id = Terminal.lerInteiro("ID do jogo", 1, Integer.MAX_VALUE);

        Terminal.imprimir("  Qual índice deseja utilizar para localizar o registro?");
        Terminal.imprimir("   1 - Árvore B+");
        Terminal.imprimir("   2 - Hash Estendido");
        boolean usarHash = Terminal.lerInteiro("Opção", 1, 2) == 2;

        Jogo atual = usarHash ? arquivoIndexado.readComHash(id) : arquivoIndexado.readComArvoreB(id);
        if (atual == null) {
            Terminal.erro("Nenhum registro válido com o ID " + id + ".");
            Terminal.pausar();
            return;
        }

        Terminal.imprimir(atual.toString());
        Terminal.linha();
        Terminal.imprimir("  Pressione ENTER para manter o valor atual de cada campo.");
        Terminal.linha();

        Jogo novo = new Jogo(
                atual.getId(),
                Terminal.lerTextoOuManter("Nome", atual.getNome()),
                Terminal.lerTextoOuManter("Desenvolvedora", atual.getDesenvolvedora()),
                Terminal.lerTextoOuManter("Faixa de jogadores", atual.getFaixaJogadores()),
                Terminal.lerDataOuManter("Lançamento", atual.getDataLancamento()),
                Terminal.lerListaOuManter("Gêneros", atual.getGeneros()),
                Terminal.lerFloatOuManter("Preço (US$)", atual.getPreco()),
                Terminal.lerInteiroNaoNegativoOuManter("Avaliações positivas", atual.getAvaliacoesPositivas()),
                Terminal.lerInteiroNaoNegativoOuManter("Avaliações negativas", atual.getAvaliacoesNegativas())
        );

        boolean ok = arquivoIndexado.update(novo, usarHash);
        Terminal.linha();
        if (ok) {
            Terminal.sucesso("Registro atualizado com sucesso e índices sincronizados!");
        } else {
            Terminal.erro("Falha ao atualizar o registro.");
        }
        Terminal.pausar();
    }

    // ----------------------------------------------------------- 8. excluir

    private static void excluir() throws Exception {
        Terminal.titulo("excluir jogo com apoio de índice");
        int id = Terminal.lerInteiro("ID do jogo", 1, Integer.MAX_VALUE);

        Terminal.imprimir("  Qual índice deseja utilizar para localizar o registro?");
        Terminal.imprimir("   1 - Árvore B+");
        Terminal.imprimir("   2 - Hash Estendido");
        boolean usarHash = Terminal.lerInteiro("Opção", 1, 2) == 2;

        Jogo jogo = usarHash ? arquivoIndexado.readComHash(id) : arquivoIndexado.readComArvoreB(id);
        if (jogo == null) {
            Terminal.erro("Nenhum registro válido com o ID " + id + ".");
            Terminal.pausar();
            return;
        }

        Terminal.imprimir(jogo.toString());
        Terminal.linha();
        if (!Terminal.lerSimNao("Confirma a exclusão deste registro?", false)) {
            Terminal.aviso("Operação cancelada.");
            Terminal.pausar();
            return;
        }

        arquivoIndexado.delete(id, usarHash);
        Terminal.sucesso("Registro removido logicamente da base e excluído da Árvore B+, Hash e Listas Invertidas.");
        Terminal.pausar();
    }

    // ------------------------------------------------------ 9. estatísticas

    private static void estatisticas() throws Exception {
        Terminal.titulo("estatísticas do arquivo binário");
        ArquivoSequencial.Estatisticas e = arquivoIndexado.getArquivoDados().estatisticas();

        Terminal.imprimir("  Arquivo ................. " + new File(CAMINHO_DB).getAbsolutePath());
        Terminal.imprimir("  Tamanho total ........... " + Terminal.formatarBytes(e.tamanhoArquivo));
        Terminal.imprimir("  Último ID do cabeçalho .. " + arquivoIndexado.getArquivoDados().getUltimoId());
        Terminal.linha();
        Terminal.imprimir("  Registros válidos ....... " + e.validos
                + "  (" + Terminal.formatarBytes(e.bytesUteis) + ")");
        Terminal.imprimir("  Registros com lápide .... " + e.excluidos
                + "  (" + Terminal.formatarBytes(e.bytesDesperdicados) + ")");
        Terminal.imprimir(String.format("  Espaço desperdiçado ..... %.2f%%", e.percentualDesperdicado()));

        if (e.validos > 0) {
            Terminal.imprimir("  Tamanho médio do registro " + (e.bytesUteis / e.validos) + " bytes");
        }
        Terminal.pausar();
    }

    // -------------------------------------------------- 10. ordenação externa

    private static void ordenacaoExterna() throws Exception {
        Terminal.titulo("ordenação externa");
        ArquivoSequencial.Estatisticas antes = arquivoIndexado.getArquivoDados().estatisticas();
        if (antes.validos == 0) {
            Terminal.aviso("A base está vazia. Nada a ordenar.");
            Terminal.pausar();
            return;
        }

        int caminhos = Terminal.lerInteiroOuPadrao("Número de caminhos (N)", 4, 2, 64);
        int memoria  = Terminal.lerInteiroOuPadrao("Registros em memória primária (M)", 100, 1, 1_000_000);

        ChaveOrdenacao[] chaves = ChaveOrdenacao.values();
        for (int i = 0; i < chaves.length; i++) {
            Terminal.imprimir("   " + (i + 1) + " - " + chaves[i].getDescricao());
        }
        int escolha = Terminal.lerInteiroOuPadrao("Chave", 1, 1, chaves.length);
        ChaveOrdenacao chave = chaves[escolha - 1];

        boolean substituicao = Terminal.lerSimNao(
                "Usar seleção por substituição (blocos de tamanho variável)?", true);

        Terminal.imprimir("  Executando...");
        arquivoIndexado.getArquivoDados().close();
        try {
            OrdenacaoExterna.ordenar(CAMINHO_DB, caminhos, memoria, chave, substituicao);
        } finally {
            arquivoIndexado.getArquivoDados().reabrir();
            arquivoIndexado.reconstruirIndices(); // Reindexa os novos offsets após reordenar
        }

        Terminal.sucesso("Ordenação concluída e índices reconstruídos com as novas posições em disco!");
        Terminal.pausar();
    }
}