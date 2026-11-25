package br.com.equipe.farmarede.fontes.simulador;

import br.com.equipe.farmarede.fontes.operacional.Cliente;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.random.RandomGenerator;

/** Gera clientes sintéticos (nomes e cidades fictícios, e-mail único em exemplo.com). */
final class GeradorClientes {

  private record Cidade(String nome, String uf) {}

  private static final List<String> NOMES =
      List.of("Mariana", "Rafael", "Juliana", "Pedro", "Beatriz", "Lucas", "Fernanda", "Tiago");
  private static final List<String> SOBRENOMES =
      List.of("Almeida", "Barbosa", "Cardoso", "Ferreira", "Gomes", "Oliveira", "Pereira", "Silva");
  private static final List<Cidade> CIDADES =
      List.of(
          new Cidade("São Paulo", "SP"),
          new Cidade("Campinas", "SP"),
          new Cidade("Santos", "SP"),
          new Cidade("Rio de Janeiro", "RJ"),
          new Cidade("Niterói", "RJ"));
  private static final int IDADE_MINIMA = 18;
  private static final int FAIXA_DE_IDADE = 62;
  private static final int DIAS_NO_ANO = 365;
  private static final int TAMANHO_SUFIXO_EMAIL = 8;

  Cliente gerar(final RandomGenerator sorteio) {
    final String nome = sortear(NOMES, sorteio);
    final String sobrenome = sortear(SOBRENOMES, sorteio);
    final Cidade cidade = sortear(CIDADES, sorteio);
    final Cliente cliente = new Cliente();
    cliente.setNome(nome + " " + sobrenome);
    // Sufixo aleatório garante a UNIQUE de e-mail mesmo com nomes repetidos.
    cliente.setEmail(
        "%s.%s.%s@exemplo.com"
            .formatted(
                slug(nome),
                slug(sobrenome),
                UUID.randomUUID().toString().substring(0, TAMANHO_SUFIXO_EMAIL)));
    cliente.setCidade(cidade.nome());
    cliente.setUf(cidade.uf());
    cliente.setDataNascimento(
        LocalDate.now()
            .minusYears(IDADE_MINIMA + sorteio.nextInt(FAIXA_DE_IDADE))
            .minusDays(sorteio.nextInt(DIAS_NO_ANO)));
    return cliente;
  }

  private static <T> T sortear(final List<T> opcoes, final RandomGenerator sorteio) {
    return opcoes.get(sorteio.nextInt(opcoes.size()));
  }

  private static String slug(final String texto) {
    return Normalizer.normalize(texto, Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "")
        .toLowerCase(Locale.ROOT);
  }
}
