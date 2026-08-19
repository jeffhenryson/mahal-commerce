"""Pools de nomes brasileiros reutilizados pelos scripts de usuários e clientes."""

FIRST_NAMES = [
    "João", "Maria", "José", "Ana", "Pedro", "Paula", "Lucas", "Juliana", "Carlos", "Fernanda",
    "Rafael", "Camila", "Bruno", "Larissa", "Diego", "Beatriz", "Felipe", "Amanda", "Gustavo",
    "Patrícia", "Rodrigo", "Vanessa", "Thiago", "Priscila", "Marcelo", "Renata", "André", "Débora",
    "Leonardo", "Aline", "Vinícius", "Carolina", "Eduardo", "Tatiane", "Fábio", "Bianca", "Daniel",
    "Letícia", "Marcos", "Vivian", "Alexandre", "Natália", "Rogério", "Cristina", "Gabriel",
    "Isabela", "Henrique", "Michele", "Sérgio", "Adriana", "Wesley", "Jéssica", "Anderson",
    "Simone", "Fabrício", "Luana", "Guilherme", "Raquel", "Igor", "Sabrina",
]

LAST_NAMES = [
    "Silva", "Santos", "Oliveira", "Souza", "Rodrigues", "Ferreira", "Alves", "Pereira", "Lima",
    "Gomes", "Costa", "Ribeiro", "Martins", "Carvalho", "Almeida", "Lopes", "Soares", "Fernandes",
    "Vieira", "Barbosa", "Rocha", "Dias", "Nascimento", "Moreira", "Nunes", "Marques", "Machado",
    "Mendes", "Freitas", "Cardoso", "Ramos", "Gonçalves", "Teixeira", "Correia", "Pinto",
    "Araújo", "Castro", "Andrade", "Monteiro", "Cavalcanti",
]


def full_name(rng):
    return f"{rng.choice(FIRST_NAMES)} {rng.choice(LAST_NAMES)}"
