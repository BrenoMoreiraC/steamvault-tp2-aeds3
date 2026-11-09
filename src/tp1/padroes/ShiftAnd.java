package tp1.padroes;

import java.util.ArrayList;
import java.util.List;

public class ShiftAnd {

    // Método de busca utilizando o algoritmo Shift-And (Bit-parallel)
    public static List<Integer> buscar(String texto, String padrao) {
        List<Integer> resultados = new ArrayList<>();
        int m = padrao.length();
        int n = texto.length();

        // O Shift-And clássico com 'long' suporta padrões de até 64 caracteres
        if (m == 0 || n == 0 || m > 64) {
            return resultados; 
        }

        // Tabela de máscaras para os caracteres (considerando a tabela ASCII estendida)
        long[] mascara = new long[256];
        for (int i = 0; i < 256; i++) {
            mascara[i] = ~0L; // Inicializa todos os bits com 1 (nenhum caractere corresponde inicialmente)
        }

        // Preenche as máscaras para cada caractere presente no padrão
        for (int i = 0; i < m; i++) {
            mascara[padrao.charAt(i)] &= ~(1L << i);
        }

        long R = ~0L; // Estado atual da busca
        long bitMascara = 1L << (m - 1); // Bit que indica que o padrão completo foi encontrado

        // Varre o texto caractere por caractere
        for (int i = 0; i < n; i++) {
            // Atualiza o estado R usando a máscara do caractere atual do texto
            R = ((R << 1) | 1L) | mascara[texto.charAt(i)];

            // Se o bit mais significativo de R for 0, o padrão foi casado
            if ((R & bitMascara) == 0) {
                resultados.add(i - m + 1); // Adiciona a posição inicial da ocorrência
            }
        }

        return resultados;
    }
}