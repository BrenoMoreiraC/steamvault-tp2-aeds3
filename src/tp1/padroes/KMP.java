package tp1.padroes;

import java.util.ArrayList;
import java.util.List;

public class KMP {

    // Método principal de busca que retorna as posições onde o padrão foi encontrado
    public static List<Integer> buscar(String texto, String padrao) {
        List<Integer> resultados = new ArrayList<>();
        int M = padrao.length();
        int N = texto.length();

        int lps[] = new int[M];
        computarLPS(padrao, M, lps);

        int i = 0; // índice para texto[]
        int j = 0; // índice para padrao[]
        while (i < N) {
            if (padrao.charAt(j) == texto.charAt(i)) {
                j++;
                i++;
            }
            if (j == M) {
                resultados.add(i - j); // Encontrou o padrão na posição i - j
                j = lps[j - 1];
            } else if (i < N && padrao.charAt(j) != texto.charAt(i)) {
                if (j != 0)
                    j = lps[j - 1];
                else
                    i++;
            }
        }
        return resultados;
    }

    // Método auxiliar que faltava para preencher o vetor lps (Longest Prefix Suffix)
    private static void computarLPS(String padrao, int M, int lps[]) {
        int len = 0;
        int i = 1;
        lps[0] = 0; // lps[0] é sempre 0

        while (i < M) {
            if (padrao.charAt(i) == padrao.charAt(len)) {
                len++;
                lps[i] = len;
                i++;
            } else {
                if (len != 0) {
                    len = lps[len - 1];
                } else {
                    lps[i] = 0;
                    i++;
                }
            }
        }
    }
}