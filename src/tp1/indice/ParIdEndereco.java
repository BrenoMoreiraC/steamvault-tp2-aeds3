package tp1.indice;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * Representa um registro do índice primário: (ID, Endereço no arquivo de dados).
 * Usado tanto pela Árvore B+ quanto pelo Hashing Estendido.
 */
public class ParIdEndereco implements Comparable<ParIdEndereco> {
    private int id;
    private long endereco; // offset/posição do byte de lápide no arquivo de dados

    public static final int TAMANHO_BYTES = Integer.BYTES + Long.BYTES; // 4 + 8 = 12 bytes

    public ParIdEndereco() {
        this(-1, -1);
    }

    public ParIdEndereco(int id, long endereco) {
        this.id = id;
        this.endereco = endereco;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public long getEndereco() { return endereco; }
    public void setEndereco(long endereco) { this.endereco = endereco; }

    public void write(DataOutput out) throws IOException {
        out.writeInt(id);
        out.writeLong(endereco);
    }

    public void read(DataInput in) throws IOException {
        this.id = in.readInt();
        this.endereco = in.readLong();
    }

    @Override
    public int compareTo(ParIdEndereco outro) {
        return Integer.compare(this.id, outro.id);
    }
}