package tp1.arquivo;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import tp1.indice.ArvoreBPlus;
import tp1.indice.HashExtensivel;
import tp1.indice.ListaInvertida;
import tp1.modelo.Jogo;

/**
 * Gerenciador de Arquivo Indexado que coordena o arquivo mestre de dados
 * sequencial junto às estruturas de indexação: Árvore B+, Hash Estendido
 * e duas Listas Invertidas (Gêneros e Desenvolvedoras).
 */
public class ArquivoIndexado implements AutoCloseable {

    private final ArquivoSequencial<Jogo> arquivoDados;
    private final ArvoreBPlus arvoreBPlus;
    private final HashExtensivel hashExtensivel;
    private final ListaInvertida listaGeneros;
    private final ListaInvertida listaDesenvolvedoras;

    private static final int ORDEM_ARVORE_B = 8;
    // 5% da base de 55.744 jogos = ~2.787 registros por bucket (conforme enunciado)
    // 5% de 1.000 jogos = 50 registros por cesto (conforme especificação do TP2)
private static final int CAPACIDADE_CESTO_PADRAO = 50;

    public ArquivoIndexado(String caminhoBase) throws Exception {
        this.arquivoDados = new ArquivoSequencial<>(caminhoBase, Jogo.class);

        File pastaDados = new File(caminhoBase).getParentFile();
        if (pastaDados != null && !pastaDados.exists()) {
            pastaDados.mkdirs();
        }

        String prefixoIndices = (pastaDados != null ? pastaDados.getPath() + "/" : "") + "indice_";

        this.arvoreBPlus = new ArvoreBPlus(prefixoIndices + "arvore_bplus.db", ORDEM_ARVORE_B);
        this.hashExtensivel = new HashExtensivel(prefixoIndices + "hash", CAPACIDADE_CESTO_PADRAO);
        this.listaGeneros = new ListaInvertida(prefixoIndices + "lista_generos.db");
        this.listaDesenvolvedoras = new ListaInvertida(prefixoIndices + "lista_desenvolvedoras.db");
    }

    public ArquivoSequencial<Jogo> getArquivoDados() {
        return arquivoDados;
    }

 // =========================================================================
    // RECONSTRUÇÃO / INDEXAÇÃO INICIAL OTIMIZADA COM BUFFER
    // =========================================================================

    public int reconstruirIndices() throws Exception {
        return reconstruirIndices(Integer.MAX_VALUE);
    }

    public int reconstruirIndices(int limiteRegistros) throws Exception {
        int contador = 0;
        java.io.RandomAccessFile raw = new java.io.RandomAccessFile(arquivoDados.getCaminho(), "r");
        long tamanho = raw.length();
        raw.seek(ArquivoSequencial.TAMANHO_CABECALHO);

        // Mapas em memória para carga ultrarrápida das Listas Invertidas
        java.util.Map<String, java.util.List<Integer>> mapaGeneros = new java.util.HashMap<>();
        java.util.Map<String, java.util.List<Integer>> mapaDevs = new java.util.HashMap<>();

        System.out.println("  Indexando B+ e Hash...");
        while (raw.getFilePointer() < tamanho && contador < limiteRegistros) {
            long enderecoRegistro = raw.getFilePointer();
            byte lapide = raw.readByte();
            int tamRegistro = raw.readInt();

            if (lapide == ArquivoSequencial.LAPIDE_VALIDO) {
                byte[] bytes = new byte[tamRegistro];
                raw.readFully(bytes);

                Jogo jogo = new Jogo();
                jogo.fromByteArray(bytes);

                // Indexação Primária direta
                arvoreBPlus.inserir(jogo.getId(), enderecoRegistro);
                hashExtensivel.inserir(jogo.getId(), enderecoRegistro);

                // Acúmulo em buffer para as Listas Invertidas
                if (jogo.getGeneros() != null) {
                    for (String g : jogo.getGeneros()) {
                        if (g != null && !g.trim().isEmpty()) {
                            mapaGeneros.computeIfAbsent(g.trim().toLowerCase(), k -> new ArrayList<>()).add(jogo.getId());
                        }
                    }
                }
                if (jogo.getDesenvolvedora() != null && !jogo.getDesenvolvedora().trim().isEmpty()) {
                    mapaDevs.computeIfAbsent(jogo.getDesenvolvedora().trim().toLowerCase(), k -> new ArrayList<>()).add(jogo.getId());
                }

                contador++;
                if (contador % 5000 == 0) {
                    System.out.println("    " + contador + " jogos processados...");
                }
            } else {
                raw.skipBytes(tamRegistro);
            }
        }
        raw.close();

        // Despeja as Listas Invertidas de uma só vez no disco
        System.out.println("  Persistindo Listas Invertidas em disco...");
        for (var entry : mapaGeneros.entrySet()) {
            listaGeneros.inserirListaCompleta(entry.getKey(), entry.getValue());
        }
        for (var entry : mapaDevs.entrySet()) {
            listaDesenvolvedoras.inserirListaCompleta(entry.getKey(), entry.getValue());
        }

        return contador;
    }

private void indexarListasInvertidas(Jogo jogo) throws IOException {
        // Lista Invertida 1: Gêneros (já é String[])
        if (jogo.getGeneros() != null) {
            for (String g : jogo.getGeneros()) {
                if (g != null && !g.trim().isEmpty()) {
                    listaGeneros.inserir(g.trim(), jogo.getId());
                }
            }
        }

        // Lista Invertida 2: Desenvolvedora
        if (jogo.getDesenvolvedora() != null && !jogo.getDesenvolvedora().isEmpty()) {
            listaDesenvolvedoras.inserir(jogo.getDesenvolvedora().trim(), jogo.getId());
        }
    }

    // =========================================================================
    // CREATE
    // =========================================================================

    public int create(Jogo jogo) throws Exception {
        // Grava no arquivo sequencial e descobre o offset físico
        long endereco = arquivoDados.createRetornandoEndereco(jogo);

        // Atualiza os índices primários
        arvoreBPlus.inserir(jogo.getId(), endereco);
        hashExtensivel.inserir(jogo.getId(), endereco);

        // Atualiza as listas invertidas
        indexarListasInvertidas(jogo);

        return jogo.getId();
    }

    // =========================================================================
    // READ (COM APOIO DOS ÍNDICES PRIMÁRIOS)
    // =========================================================================

    /**
     * Busca com apoio da ÁRVORE B+. Custo O(log n) acessos a disco.
     */
    public Jogo readComArvoreB(int id) throws Exception {
        long endereco = arvoreBPlus.buscar(id);
        if (endereco < 0) return null;
        return arquivoDados.readPorEndereco(endereco);
    }

    /**
     * Busca com apoio do HASHING ESTENDIDO. Custo O(1) médio acessos a disco.
     */
    public Jogo readComHash(int id) throws Exception {
        long endereco = hashExtensivel.buscar(id);
        if (endereco < 0) return null;
        return arquivoDados.readPorEndereco(endereco);
    }

    // =========================================================================
    // CONSULTAS COM APOIO DAS LISTAS INVERTIDAS
    // =========================================================================

    public List<Jogo> buscarPorGenero(String genero) throws Exception {
        List<Integer> ids = listaGeneros.buscar(genero);
        return carregarJogosPorIds(ids);
    }

    public List<Jogo> buscarPorDesenvolvedora(String desenvolvedora) throws Exception {
        List<Integer> ids = listaDesenvolvedoras.buscar(desenvolvedora);
        return carregarJogosPorIds(ids);
    }

    /**
     * Busca composta combinando Gênero E Desenvolvedora (Interseção AND).
     */
    public List<Jogo> buscarCompostaIntersecao(String genero, String desenvolvedora) throws Exception {
        List<Integer> idsGenero = listaGeneros.buscar(genero);
        List<Integer> idsDev = listaDesenvolvedoras.buscar(desenvolvedora);
        List<Integer> combinados = ListaInvertida.intersecao(idsGenero, idsDev);
        return carregarJogosPorIds(combinados);
    }

    /**
     * Busca composta combinando Gênero OU Desenvolvedora (União OR).
     */
    public List<Jogo> buscarCompostaUniao(String genero, String desenvolvedora) throws Exception {
        List<Integer> idsGenero = listaGeneros.buscar(genero);
        List<Integer> idsDev = listaDesenvolvedoras.buscar(desenvolvedora);
        List<Integer> combinados = ListaInvertida.uniao(idsGenero, idsDev);
        return carregarJogosPorIds(combinados);
    }

    private List<Jogo> carregarJogosPorIds(List<Integer> ids) throws Exception {
        List<Jogo> jogos = new ArrayList<>();
        for (int id : ids) {
            // Busca o offset via índice para recuperar diretamente
            Jogo j = readComArvoreB(id);
            if (j != null) {
                jogos.add(j);
            }
        }
        return jogos;
    }

    // =========================================================================
    // UPDATE
    // =========================================================================

    /**
     * Atualização com apoio de índice:
     * O usuário indica qual estrutura quer usar para localizar o registro.
     */
    public boolean update(Jogo novo, boolean usarHash) throws Exception {
        long enderecoAntigo = usarHash ? hashExtensivel.buscar(novo.getId()) : arvoreBPlus.buscar(novo.getId());
        if (enderecoAntigo < 0) return false;

        // Recupera o registro antigo para saber o que atualizar nas listas invertidas se mudou
        Jogo antigo = arquivoDados.readPorEndereco(enderecoAntigo);

        long novoEndereco = arquivoDados.updatePorEndereco(enderecoAntigo, novo);

        // Se mudou de lugar (foi pro fim do arquivo), atualiza os índices primários
        if (novoEndereco != enderecoAntigo) {
            arvoreBPlus.atualizar(novo.getId(), novoEndereco);
            hashExtensivel.atualizar(novo.getId(), novoEndereco);
        }

        // Se gêneros ou desenvolvedora foram alterados, sincroniza nas listas invertidas
        if (antigo != null) {
            String genAntigo = antigo.getGenerosConcatenados();
            String genNovo = novo.getGenerosConcatenados();

            if (!genAntigo.equalsIgnoreCase(genNovo)) {
                removerListasInvertidas(antigo);
                indexarListasInvertidas(novo);
            } else if (!antigo.getDesenvolvedora().equalsIgnoreCase(novo.getDesenvolvedora())) {
                listaDesenvolvedoras.remover(antigo.getDesenvolvedora(), antigo.getId());
                listaDesenvolvedoras.inserir(novo.getDesenvolvedora(), novo.getId());
            }
        }

        return true;
    }

   private void removerListasInvertidas(Jogo jogo) throws IOException {
        if (jogo.getGeneros() != null) {
            for (String g : jogo.getGeneros()) {
                if (g != null && !g.trim().isEmpty()) {
                    listaGeneros.remover(g.trim(), jogo.getId());
                }
            }
        }
        if (jogo.getDesenvolvedora() != null && !jogo.getDesenvolvedora().isEmpty()) {
            listaDesenvolvedoras.remover(jogo.getDesenvolvedora().trim(), jogo.getId());
        }
    }

    // =========================================================================
    // DELETE
    // =========================================================================

    /**
     * Exclusão com apoio de índice:
     * Localiza diretamente pelo índice indicado pelo usuário e remove.
     */
    public boolean delete(int id, boolean usarHash) throws Exception {
        long endereco = usarHash ? hashExtensivel.buscar(id) : arvoreBPlus.buscar(id);
        if (endereco < 0) return false;

        Jogo jogo = arquivoDados.readPorEndereco(endereco);

        // Marca a lápide no arquivo de dados
        arquivoDados.deletePorEndereco(endereco);

        // Invalida nos índices primários
        arvoreBPlus.excluir(id);
        hashExtensivel.excluir(id);

        // Remove das listas invertidas
        if (jogo != null) {
            removerListasInvertidas(jogo);
        }

        return true;
    }

    @Override
    public void close() throws Exception {
        if (arquivoDados != null) arquivoDados.close();
        if (arvoreBPlus != null) arvoreBPlus.fechar();
        if (hashExtensivel != null) hashExtensivel.fechar();
        if (listaGeneros != null) listaGeneros.fechar();
        if (listaDesenvolvedoras != null) listaDesenvolvedoras.fechar();
    }
}