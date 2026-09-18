#!/usr/bin/env python3
from __future__ import annotations

import html
import json
import re
import shutil
import sys
from pathlib import Path

UPSTREAM_REPO = "https://github.com/shendds/100htmlgameshub"
UPSTREAM_COMMIT = "cdf426cc619f3c713af1d73d485112444bace7c3"
EXPECTED_GAMES = 100

TITLE_PT = {
    1:"Cobra",2:"Duelo de Raquetes",3:"Quebra-Blocos",4:"Blocos",5:"Invasores do Espaço",
    6:"Caça no Labirinto",7:"Sapo na Estrada",8:"Rochas Espaciais",9:"Voo do Pássaro",
    10:"Corrida do Dinossauro",11:"Acerte a Toupeira",12:"Sequência de Luzes",13:"Jogo da Memória",
    14:"Campo Minado",15:"Sudoku",16:"2048",17:"Quatro em Linha",18:"Jogo da Velha",
    19:"Damas",20:"Xadrez",21:"Quebra-Cabeça Deslizante",22:"Quebra-Cabeça de Peças",
    23:"Caça-Palavras",24:"Forca",25:"Palavra Embaralhada",26:"Adivinhe o Número",
    27:"Combine as Cores",28:"Memória de Padrões",29:"Labirinto",30:"Apague as Luzes",
    31:"Torre de Hanói",32:"Tempo de Reação",33:"Quiz de Matemática",34:"Velocidade de Digitação",
    35:"Pontos e Caixas",36:"Reversi",37:"Batalha Naval",38:"Portas Lógicas",39:"Nonograma",
    40:"Memória de Sequência",41:"Batalha de Tanques",42:"Caça ao Pato",43:"Atirador de Bolhas",
    44:"Corrida de Carros",45:"Corte de Frutas",46:"Defesa da Torre",47:"Tiro com Arco",
    48:"Pinball",49:"Hóquei de Mesa",50:"Sinuca",51:"Quebra-Tijolos",52:"Queda de Moedas",
    53:"Estoure o Balão",54:"Galeria de Tiro",55:"Defensor Espacial",56:"Caçador de Zumbis",
    57:"Labirinto Laser",58:"Canhão",59:"Bola Gravitacional",60:"Bola Saltitante",
    61:"Blackjack",62:"Paciência",63:"Guerra de Cartas",64:"Vai Pescar",65:"Bate!",
    66:"Lançar Dados",67:"Cinco Dados",68:"Pôquer",69:"Concentração",70:"Mico",
    71:"Velocidade",72:"Rami",73:"Oito Maluco",74:"Cores e Números",75:"Cribbage",
    76:"Ludo",77:"Cobras e Escadas",78:"Gamão",79:"Bingo",80:"Caça-Níquel",
    81:"Plataforma",82:"Clique no Biscoito",83:"Fazenda Ociosa",84:"Pedra, Papel e Tesoura",
    85:"Quiz de Conhecimentos",86:"Verdadeiro ou Falso",87:"Roleta",88:"Misturador de Cores",
    89:"Tela de Desenho",90:"Arte em Pixels",91:"Jogo das Cores",92:"Bateria Virtual",
    93:"Piano Virtual",94:"Metrônomo",95:"Toque no Ritmo",96:"Quiz de Geografia",
    97:"Quiz de Sons de Animais",98:"Adivinhe a Bandeira",99:"Combine os Emojis",100:"Sala de Fuga",
}

CATEGORY_PT = {
    "arcade": "Arcade",
    "puzzle": "Quebra-cabeça",
    "action": "Ação",
    "card": "Cartas e tabuleiro",
    "fun": "Diversão",
}

COMMON_PT = {
    "← Back to Menu": "← Voltar ao menu",
    "Back to Menu": "Voltar ao menu",
    "Score:": "Pontos:",
    "Score": "Pontos",
    "Best:": "Recorde:",
    "Best": "Recorde",
    "Speed:": "Velocidade:",
    "Speed": "Velocidade",
    "Level:": "Nível:",
    "Level": "Nível",
    "Lives:": "Vidas:",
    "Lives": "Vidas",
    "Question:": "Pergunta:",
    "Question": "Pergunta",
    "Streak:": "Sequência:",
    "Streak": "Sequência",
    "Wins:": "Vitórias:",
    "Losses:": "Derrotas:",
    "Moves:": "Jogadas:",
    "Moves": "Jogadas",
    "Attempts:": "Tentativas:",
    "Attempts": "Tentativas",
    "Time:": "Tempo:",
    "Time": "Tempo",
    "Start Game": "Iniciar jogo",
    "Start Quiz": "Iniciar quiz",
    "Start": "Iniciar",
    "Restart": "Reiniciar",
    "Play Again": "Jogar novamente",
    "New Game": "Novo jogo",
    "New Round": "Nova rodada",
    "Game Over": "Fim de jogo",
    "Correct!": "Correto!",
    "Wrong!": "Errado!",
    "Correct": "Correto",
    "Wrong": "Errado",
    "Time's up!": "Tempo esgotado!",
    "Answer:": "Resposta:",
    "Hint:": "Dica:",
    "Hint": "Dica",
    "Submit": "Confirmar",
    "Skip": "Pular",
    "Easy": "Fácil",
    "Medium": "Normal",
    "Hard": "Difícil",
    "Player 1": "Jogador 1",
    "Player 2": "Jogador 2",
    "Computer": "CPU",
    "CPU": "CPU",
    "You": "Você",
    "Draw!": "Empate!",
    "Draw": "Empate",
    "Final Score:": "Pontuação final:",
    "Final Score": "Pontuação final",
    "Press Start to begin!": "Pressione Iniciar para começar!",
    "Press SPACE or click to start": "Pressione OK para começar",
    "Click or press SPACE to start": "Pressione OK para começar",
    "Press SPACE or the button to start": "Pressione OK para começar",
    "Click or SPACE to flap": "Pressione OK para voar",
    "SPACE / ↑ to jump | ↓ to duck": "OK / ↑ para pular | ↓ para abaixar",
    "Arrow keys or WASD to move": "Use as setas do controle para mover",
    "SPACE to launch": "OK para lançar",
    "Click and drag to select words!": "Segure OK e mova para selecionar palavras!",
    "All words found!": "Todas as palavras foram encontradas!",
    "Guess the word!": "Adivinhe a palavra!",
    "Not quite! Try again.": "Ainda não! Tente novamente.",
    "Out of attempts!": "Acabaram as tentativas!",
    "Too low! Go higher.": "Muito baixo! Tente um número maior.",
    "Too high! Go lower.": "Muito alto! Tente um número menor.",
    "Keep practicing!": "Continue praticando!",
    "Not bad!": "Nada mal!",
    "Good job!": "Bom trabalho!",
    "Great score!": "Ótima pontuação!",
    "Perfect!": "Perfeito!",
    "General": "Geral",
    "Geography": "Geografia",
    "Science": "Ciências",
    "Physics": "Física",
    "Chemistry": "Química",
    "Biology": "Biologia",
    "History": "História",
    "Math": "Matemática",
    "Art": "Arte",
    "Culture": "Cultura",
    "Literature": "Literatura",
    "Technology": "Tecnologia",
    "Astronomy": "Astronomia",
    "True": "Verdadeiro",
    "False": "Falso",
}

REMOTE_LINK_RE = re.compile(
    r"""(?:src|href)\s*=\s*["']https?://|url\(\s*["']?https?://""",
    re.IGNORECASE,
)
GAME_ROW_RE = re.compile(
    r"""\{\s*id:\s*(\d+),\s*name:\s*"([^"]+)",\s*icon:\s*"([^"]+)",\s*cat:\s*"([^"]+)"[^}]*ready:\s*true\s*\}"""
)
CANVAS_RE = re.compile(
    r"""<canvas\b[^>]*\bwidth=["']?(\d+)["']?[^>]*\bheight=["']?(\d+)["']?[^>]*>""",
    re.IGNORECASE,
)

CPU_PATCH_GAMES = {
    "02-pong","17-connect-four","18-tic-tac-toe","19-checkers","20-chess",
    "35-dots-and-boxes","36-reversi","76-ludo","77-snakes-and-ladders",
}

DIFFICULTY_GAMES = {
    "01-snake","03-breakout","05-space-invaders","07-frogger","08-asteroids",
    "09-flappy-bird","10-dino-runner","41-tank-battle","43-bubble-shooter",
    "44-car-racing","45-fruit-ninja","46-tower-defense","47-archery","48-pinball",
    "49-air-hockey","51-brick-breaker","54-shooting-gallery","55-space-defender",
    "56-zombie-shooter","58-cannon-ball","59-gravity-ball","60-bouncing-ball","81-platformer",
}

def strip_remote_fonts(text: str) -> str:
    text = re.sub(r"""<link\b[^>]*href=["']https://fonts\.googleapis\.com[^>]*>\s*""", "", text, flags=re.I)
    text = re.sub(r"""<link\b[^>]*href=["']https://fonts\.gstatic\.com[^>]*>\s*""", "", text, flags=re.I)
    text = re.sub(r"""<link\b[^>]*rel=["']preconnect["'][^>]*fonts\.(?:googleapis|gstatic)\.com[^>]*>\s*""", "", text, flags=re.I)
    text = re.sub(r"""@import\s+url\([^)]*fonts\.googleapis\.com[^)]*\)\s*;?""", "", text, flags=re.I)
    return text

def translate_common(text: str) -> str:
    text = text.replace('lang="en"', 'lang="pt-BR"').replace("lang='en'", "lang='pt-BR'")
    for src in sorted(COMMON_PT, key=len, reverse=True):
        text = text.replace(src, COMMON_PT[src])
    return text

def replace_const_array(text: str, const_name: str, replacement: str) -> str:
    pattern = r"const\s+" + re.escape(const_name) + r"\s*=\s*\[(?:.|\n|\r)*?\];"
    return re.sub(pattern, replacement, text, count=1, flags=re.S)

def localize_content(slug: str, text: str) -> str:
    if slug == "23-word-search":
        text = re.sub(
            r"const WORD_POOL = \[[^;]+\];",
            "const WORD_POOL = ['PROGRAMA','ALGORITMO','FUNCAO','VARIAVEL','MATRIZ','OBJETO','TEXTO','NUMERO','LOGICA','REPETIR','CLASSE','METODO','PIXEL','BINARIO','PILHA','FILA','GRAFO','DEPURAR','CODIGO','DADOS'];",
            text,
            count=1,
        )

    elif slug == "24-hangman":
        words = """const WORDS = [
            { w:'computador', h:'Máquina eletrônica para processar dados' },
            { w:'elefante', h:'Grande animal terrestre' },
            { w:'piramide', h:'Construção famosa do Egito antigo' },
            { w:'sinfonia', h:'Composição musical para orquestra' },
            { w:'algoritmo', h:'Sequência de passos para resolver um problema' },
            { w:'telescopio', h:'Instrumento usado para observar astros distantes' },
            { w:'chocolate', h:'Doce feito a partir do cacau' },
            { w:'furacao', h:'Tempestade tropical muito intensa' },
            { w:'borboleta', h:'Inseto de asas coloridas' },
            { w:'aventura', h:'Experiência emocionante' },
            { w:'teclado', h:'Usado para digitar' },
            { w:'universo', h:'Tudo que existe no espaço' },
            { w:'dinossauro', h:'Animal pré-histórico extinto' },
            { w:'montanha', h:'Grande elevação natural do terreno' },
            { w:'hospital', h:'Local onde profissionais cuidam da saúde' },
            { w:'pergunta', h:'Algo que pede uma resposta' },
            { w:'esqueleto', h:'Conjunto de ossos do corpo' },
            { w:'engenheiro', h:'Profissional que projeta e constrói soluções' },
            { w:'sanduiche', h:'Comida entre fatias de pão' },
            { w:'planeta', h:'Corpo celeste que orbita uma estrela' },
        ];"""
        text = replace_const_array(text, "WORDS", words)

    elif slug == "25-word-scramble":
        words = """const WORDS = [
            { w:'computador', h:'Máquina eletrônica para processar dados' },
            { w:'elefante', h:'Grande animal terrestre' },
            { w:'piramide', h:'Construção do Egito antigo' },
            { w:'sinfonia', h:'Composição musical para orquestra' },
            { w:'algoritmo', h:'Passos para resolver um problema' },
            { w:'telescopio', h:'Usado para observar estrelas distantes' },
            { w:'chocolate', h:'Doce feito de cacau' },
            { w:'furacao', h:'Tempestade tropical intensa' },
            { w:'borboleta', h:'Inseto de asas coloridas' },
            { w:'aventura', h:'Jornada ou experiência emocionante' },
            { w:'teclado', h:'Usado para digitar no computador' },
            { w:'universo', h:'Tudo que existe no espaço' },
            { w:'dinossauro', h:'Animal pré-histórico extinto' },
            { w:'montanha', h:'Grande elevação natural' },
            { w:'hospital', h:'Local de atendimento de saúde' },
            { w:'pergunta', h:'Algo que pede uma resposta' },
            { w:'esqueleto', h:'Estrutura de ossos do corpo' },
            { w:'engenheiro', h:'Profissional que projeta soluções' },
            { w:'sanduiche', h:'Comida entre fatias de pão' },
            { w:'planeta', h:'Corpo que orbita uma estrela' },
        ];"""
        text = replace_const_array(text, "WORDS", words)

    elif slug == "85-trivia-quiz":
        qs = """const QUESTIONS = [
            { q:"Qual é a capital da França?", a:"Paris", opts:["Londres","Paris","Berlim","Madri"], cat:"Geografia" },
            { q:"Qual planeta fica mais próximo do Sol?", a:"Mercúrio", opts:["Vênus","Marte","Mercúrio","Terra"], cat:"Ciências" },
            { q:"Quantos lados tem um hexágono?", a:"6", opts:["5","6","7","8"], cat:"Matemática" },
            { q:"Quem pintou a Mona Lisa?", a:"Leonardo da Vinci", opts:["Picasso","Michelangelo","Rembrandt","Leonardo da Vinci"], cat:"Arte" },
            { q:"Como H2O é conhecido?", a:"Água", opts:["Sal","Água","Oxigênio","Hidrogênio"], cat:"Ciências" },
            { q:"Em que ano terminou a Segunda Guerra Mundial?", a:"1945", opts:["1943","1944","1945","1946"], cat:"História" },
            { q:"Qual é o maior oceano da Terra?", a:"Pacífico", opts:["Atlântico","Índico","Ártico","Pacífico"], cat:"Geografia" },
            { q:"Quantos continentes são tradicionalmente considerados?", a:"7", opts:["5","6","7","8"], cat:"Geografia" },
            { q:"Qual é o símbolo químico do ouro?", a:"Au", opts:["Ag","Go","Au","Gd"], cat:"Ciências" },
            { q:"Em qual país a pizza moderna se popularizou?", a:"Itália", opts:["Grécia","Itália","França","Espanha"], cat:"Cultura" },
            { q:"Qual é a raiz quadrada de 144?", a:"12", opts:["11","12","13","14"], cat:"Matemática" },
            { q:"Quem escreveu Romeu e Julieta?", a:"Shakespeare", opts:["Dickens","Shakespeare","Austen","Tolstói"], cat:"Literatura" },
            { q:"Qual é a montanha mais alta acima do nível do mar?", a:"Everest", opts:["K2","Everest","Kilimanjaro","Fuji"], cat:"Geografia" },
            { q:"Quantas cores costumam ser listadas no arco-íris?", a:"7", opts:["5","6","7","8"], cat:"Ciências" },
            { q:"Qual idioma tem o maior número de falantes nativos?", a:"Mandarim", opts:["Inglês","Espanhol","Mandarim","Hindi"], cat:"Cultura" },
            { q:"Qual é aproximadamente a velocidade da luz?", a:"300.000 km/s", opts:["150.000 km/s","200.000 km/s","250.000 km/s","300.000 km/s"], cat:"Ciências" },
            { q:"Qual elemento tem número atômico 1?", a:"Hidrogênio", opts:["Hélio","Hidrogênio","Oxigênio","Carbono"], cat:"Ciências" },
            { q:"Em que ano o primeiro iPhone foi lançado?", a:"2007", opts:["2005","2006","2007","2008"], cat:"Tecnologia" },
            { q:"Quem descobriu a penicilina?", a:"Fleming", opts:["Pasteur","Fleming","Curie","Darwin"], cat:"Ciências" },
            { q:"Qual rio atravessa o Egito e deságua no Mediterrâneo?", a:"Nilo", opts:["Amazonas","Nilo","Yangtzé","Mississippi"], cat:"Geografia" },
        ];"""
        text = replace_const_array(text, "QUESTIONS", qs)

    elif slug == "86-true-or-false":
        qs = """const QS = [
            { q:"A Terra é plana.", a:false, cat:"Ciências" },
            { q:"A luz viaja mais rápido que o som.", a:true, cat:"Física" },
            { q:"Diamantes são formados por carbono.", a:true, cat:"Química" },
            { q:"O corpo humano adulto costuma ter 206 ossos.", a:true, cat:"Biologia" },
            { q:"O Monte Everest fica na África.", a:false, cat:"Geografia" },
            { q:"O Pacífico é o maior oceano da Terra.", a:true, cat:"Geografia" },
            { q:"Aranhas são insetos.", a:false, cat:"Biologia" },
            { q:"A Austrália é um país e também integra um continente de mesmo nome em alguns modelos geográficos.", a:true, cat:"Geografia" },
            { q:"Ao nível do mar, a água ferve perto de 100°C.", a:true, cat:"Ciências" },
            { q:"A Muralha da China é facilmente visível da Lua a olho nu.", a:false, cat:"História" },
            { q:"Morcegos são completamente cegos.", a:false, cat:"Biologia" },
            { q:"Um raio nunca cai duas vezes no mesmo lugar.", a:false, cat:"Ciências" },
            { q:"O Sol é uma estrela.", a:true, cat:"Astronomia" },
            { q:"Humanos usam apenas 10% do cérebro.", a:false, cat:"Biologia" },
            { q:"Tubarões são mamíferos.", a:false, cat:"Biologia" },
            { q:"O ouro é mais denso que o chumbo.", a:true, cat:"Química" },
            { q:"Roma é a capital da Itália.", a:true, cat:"Geografia" },
            { q:"Pi é exatamente igual a 3,14.", a:false, cat:"Matemática" },
            { q:"O som pode se propagar no vácuo.", a:false, cat:"Física" },
            { q:"Mercúrio é o planeta mais próximo do Sol.", a:true, cat:"Astronomia" },
        ];"""
        text = replace_const_array(text, "QS", qs)

    elif slug == "96-geography-quiz":
        qs = """const QS = [
            { q:'Qual é a capital da França?', a:'Paris', opts:['Londres','Paris','Berlim','Roma'], emoji:'🇫🇷' },
            { q:'Qual é o maior continente em área?', a:'Ásia', opts:['África','Ásia','Europa','Antártida'], emoji:'🌏' },
            { q:'Qual rio atravessa o Egito?', a:'Nilo', opts:['Amazonas','Nilo','Yangtzé','Mississippi'], emoji:'🏞️' },
            { q:'Qual país é atualmente o mais populoso?', a:'Índia', opts:['Estados Unidos','Índia','China','Brasil'], emoji:'👥' },
            { q:'Qual é a capital do Japão?', a:'Tóquio', opts:['Seul','Tóquio','Pequim','Osaka'], emoji:'🇯🇵' },
            { q:'Qual é o maior oceano?', a:'Pacífico', opts:['Atlântico','Pacífico','Índico','Ártico'], emoji:'🌊' },
            { q:'O Saara ocupa territórios de qual região?', a:'Vários países africanos', opts:['Somente Egito','Arábia Saudita','Vários países africanos','Índia'], emoji:'🏜️' },
            { q:'Qual é o menor país do mundo em área?', a:'Vaticano', opts:['Mônaco','Vaticano','Singapura','Luxemburgo'], emoji:'🏛️' },
            { q:'Qual país possui um número extraordinariamente alto de ilhas catalogadas?', a:'Suécia', opts:['Indonésia','Filipinas','Suécia','Japão'], emoji:'🏝️' },
            { q:'Em qual país fica o Monte Kilimanjaro?', a:'Tanzânia', opts:['Quênia','Tanzânia','Uganda','Etiópia'], emoji:'🏔️' },
            { q:'Qual país é conhecido como Terra do Sol Nascente?', a:'Japão', opts:['China','Japão','Tailândia','Coreia do Sul'], emoji:'🌅' },
            { q:'Qual é a capital da Austrália?', a:'Canberra', opts:['Sydney','Melbourne','Canberra','Brisbane'], emoji:'🇦🇺' },
            { q:'Qual rio atravessa Londres?', a:'Tâmisa', opts:['Sena','Tâmisa','Danúbio','Reno'], emoji:'🌉' },
            { q:'Qual é a capital do Brasil?', a:'Brasília', opts:['São Paulo','Rio de Janeiro','Brasília','Salvador'], emoji:'🇧🇷' },
            { q:'Em qual país fica a Torre Eiffel?', a:'França', opts:['França','Itália','Espanha','Bélgica'], emoji:'🗼' },
            { q:'Qual é o maior lago da África em área?', a:'Lago Vitória', opts:['Lago Chade','Lago Vitória','Lago Tanganica','Lago Malawi'], emoji:'🌊' },
            { q:'Em qual continente fica a maior parte do Egito?', a:'África', opts:['Ásia','África','Europa','Oceania'], emoji:'🏛️' },
            { q:'Qual é a capital do Canadá?', a:'Ottawa', opts:['Toronto','Vancouver','Ottawa','Montreal'], emoji:'🇨🇦' },
        ];"""
        text = replace_const_array(text, "QS", qs)

    elif slug == "98-flag-guesser":
        names = {
            "United States":"Estados Unidos","United Kingdom":"Reino Unido","France":"França","Germany":"Alemanha",
            "Japan":"Japão","Italy":"Itália","Spain":"Espanha","Brazil":"Brasil","Canada":"Canadá","Australia":"Austrália",
            "Mexico":"México","South Korea":"Coreia do Sul","India":"Índia","Russia":"Rússia","China":"China",
            "Turkey":"Turquia","Saudi Arabia":"Arábia Saudita","South Africa":"África do Sul","Nigeria":"Nigéria",
            "Argentina":"Argentina","Sweden":"Suécia","Norway":"Noruega","Denmark":"Dinamarca","Finland":"Finlândia",
            "Portugal":"Portugal","Greece":"Grécia","Egypt":"Egito","Thailand":"Tailândia","Vietnam":"Vietnã","Poland":"Polônia",
        }
        for a,b in names.items():
            text = text.replace("'" + a + "'", "'" + b + "'")

    elif slug == "97-animal-sound-quiz":
        pairs = {
            "'Dog'":"'Cachorro'","'Cat'":"'Gato'","'Bird'":"'Pássaro'","'Cow'":"'Vaca'",
            "'Frog'":"'Sapo'","'Duck'":"'Pato'","'Lion'":"'Leão'","'Horse'":"'Cavalo'",
            "'Woof! Woof!'":"'Au! Au!'","'Meow!'":"'Miau!'","'Tweet!'":"'Piu!'","'Moo!'":"'Muuu!'",
            "'Ribbit!'":"'Croac!'","'Quack!'":"'Quá!'","'Roar!'":"'Rugido!'","'Neigh!'":"'Relincho!'",
        }
        for a,b in pairs.items():
            text = text.replace(a,b)

    elif slug == "100-escape-room":
        rooms = """const ROOMS = [
            {
                title:'Sala 1: O Escritório Escuro',
                desc:'Você acorda em um escritório pouco iluminado. Há uma mesa pesada no centro. Na parede, um quadro mostra um relógio marcando 3:47. Um armário trancado fica no canto e existe um bilhete sobre a mesa.',
                items:['📜 Ler bilhete','🖼️ Examinar quadro','🔒 Tentar armário','🗄️ Ver gaveta'],
                puzzle:'O bilhete diz: "A chave está no tempo." Qual código de 4 dígitos abre o armário?',
                answer:'0347', hint:'Observe a hora mostrada no relógio do quadro...', reward:'🔑 Chave antiga',
                itemClues:{
                    '📜 Ler bilhete':'O bilhete diz: "A chave está no tempo. Olhe bem para as paredes."',
                    '🖼️ Examinar quadro':'O quadro mostra um relógio antigo marcando 3:47.',
                    '🔒 Tentar armário':'O armário possui uma fechadura de combinação com 4 dígitos.',
                    '🗄️ Ver gaveta':'Vazia. Há um risco quase apagado: "tempo=código".'
                }
            },
            {
                title:'Sala 2: O Laboratório',
                desc:'Você entra em um laboratório frio. Frascos marcados R=2, G=5 e B=8 ficam numa prateleira. Uma tela mostra: "Misture a fórmula: R+G+B=?". Uma porta com teclado espera pela resposta.',
                items:['🧪 Ver frascos','💻 Ler tela','🚪 Tentar porta','📋 Ler anotações'],
                puzzle:'Quanto é R + G + B? Digite a soma para abrir a porta.',
                answer:'15', hint:'Some os números dos frascos: 2 + 5 + 8.', reward:'🗝️ Cartão do laboratório',
                itemClues:{
                    '🧪 Ver frascos':'Três frascos: R=2, G=5 e B=8.',
                    '💻 Ler tela':'"Protocolo de segurança: digite R+G+B para destravar."',
                    '🚪 Tentar porta':'A fechadura eletrônica exige um código numérico.',
                    '📋 Ler anotações':'As anotações dizem: sempre some as variáveis principais.'
                }
            },
            {
                title:'Sala 3: A Biblioteca',
                desc:'Uma biblioteca enorme possui livros numerados de 1 a 26, associados às letras do alfabeto. Um enigma na parede diz: "Sou a 8ª e a 9ª letra."',
                items:['📚 Ver livros','🧩 Ler enigma','🔍 Procurar prateleira','📝 Ver registro'],
                puzzle:'O enigma pede a 8ª e a 9ª letras do alfabeto. Qual palavra de duas letras elas formam?',
                answer:'hi', hint:'8ª letra = H e 9ª letra = I.', reward:'📖 Pergaminho antigo',
                itemClues:{
                    '📚 Ver livros':'Os livros vão de 1 a 26, cada número representando uma letra.',
                    '🧩 Ler enigma':'"Sou a 8ª e a 9ª letra. Diga meu nome para avançar."',
                    '🔍 Procurar prateleira':'O 8º livro está marcado H e o 9º está marcado I.',
                    '📝 Ver registro':'Alguém escreveu: "Números viram letras".'
                }
            },
            {
                title:'Sala 4: O Cofre',
                desc:'A sala final possui um enorme cofre com trava de palavra. No chão há letras E, S, C, A, P, E. Uma placa pede para organizar as letras e formar aquilo que você procura.',
                items:['🧱 Ver letras','📋 Ler placa','🔐 Tentar cofre','🔦 Olhar ao redor'],
                puzzle:'Organize E, S, C, A, P, E para formar a palavra em inglês que significa fugir.',
                answer:'escape', hint:'A palavra começa com E e significa escapar.', reward:'🏆 Liberdade!',
                itemClues:{
                    '🧱 Ver letras':'As letras no chão são E, S, C, A, P, E.',
                    '📋 Ler placa':'"Organize as letras para escrever seu desejo."',
                    '🔐 Tentar cofre':'A trava exige a palavra correta.',
                    '🔦 Olhar ao redor':'No teto está escrito: "6 letras, começa com E".'
                }
            },
        ];"""
        text = replace_const_array(text, "ROOMS", rooms)

    return text

def apply_gameplay_patch(slug: str, text: str) -> str:
    if slug == "09-flappy-bird":
        text = text.replace(
            "const W = 340, H = 500, GRAVITY = 0.45, FLAP = -8, PIPE_W = 52, PIPE_GAP = 140, PIPE_SPD = 2.5;",
            "const W = 340, H = 500, GRAVITY = 0.42, FLAP = -7.6, PIPE_W = 50, PIPE_GAP = 172, PIPE_SPD = 2.15;"
        )
        text = text.replace(
            "let bird, pipes, score, highScore = 0, running = false, animId, bgOff = 0;",
            "let bird, pipes, score, highScore = 0, running = false, animId, bgOff = 0, spawnTimer = null;"
        )
        old = "function init() { bird = { x: 80, y: H / 2, vy: 0, angle: 0, wing: 0 }; pipes = []; score = 0; setTimeout(function sp() { if (!running) return; const th = 60 + Math.random() * (H - PIPE_GAP - 120); pipes.push({ x: W + 10, th, by: th + PIPE_GAP, done: false }); setTimeout(sp, 1600); }, 1000); }"
        new = "function init() { bird = { x: 80, y: H / 2, vy: 0, angle: 0, wing: 0 }; pipes = []; score = 0; if (spawnTimer) clearTimeout(spawnTimer); function sp() { if (!running) return; if (pipes.length < 2) { const th = 70 + Math.random() * (H - PIPE_GAP - 150); pipes.push({ x: W + 18, th, by: th + PIPE_GAP, done: false }); } spawnTimer = setTimeout(sp, 2250); } spawnTimer = setTimeout(sp, 1500); }"
        text = text.replace(old, new)
        text = text.replace(
            "function end() { cancelAnimationFrame(animId); running = false;",
            "function end() { cancelAnimationFrame(animId); if (spawnTimer) { clearTimeout(spawnTimer); spawnTimer = null; } running = false;"
        )
        # Upstream calls draw before bird/pipes exist. Initialize once so the game renders before auto-start.
        text = text.replace("        draw();\n    </script>", "        init(); draw();\n    </script>")

    elif slug == "10-dino-runner":
        text = text.replace(
            "let dino, obstacles, score, highScore = 0, running = false, animId, spd, frame, dustParts = [], clouds = [], bgX = 0;",
            "let dino, obstacles, score, highScore = 0, running = false, animId, spd, frame, dustParts = [], clouds = [], bgX = 0, spawnTimer = null;"
        )
        text = text.replace(
            "obstacles = []; score = 0; spd = 4; frame = 0; dustParts = []; clouds = [mkCloud(), { ...mkCloud(), x: 300 }, { ...mkCloud(), x: 500 }];",
            "obstacles = []; score = 0; spd = 3.6; frame = 0; dustParts = []; if (spawnTimer) clearTimeout(spawnTimer); clouds = [mkCloud(), { ...mkCloud(), x: 300 }, { ...mkCloud(), x: 500 }];"
        )
        old = "setTimeout(function sp() { if (!running) return; const tall = Math.random() < .3, cactus = Math.random() < .7; if (cactus) obstacles.push({ x: W + 20, y: GND, w: tall ? 18 : 14, h: tall ? 55 : 40, type: 'cactus' }); else obstacles.push({ x: W + 20, y: GND - 55 - Math.random() * 30, w: 48, h: 20, type: 'bird', wing: 0 }); setTimeout(sp, 700 + Math.random() * 900); }, 1000);"
        new = "function sp() { if (!running) return; if (obstacles.length < 2) { const tall = Math.random() < .26; const allowBird = score > 220; const cactus = !allowBird || Math.random() < .82; if (cactus) obstacles.push({ x: W + 30, y: GND, w: tall ? 18 : 14, h: tall ? 52 : 38, type: 'cactus' }); else obstacles.push({ x: W + 30, y: GND - 62, w: 44, h: 18, type: 'bird', wing: 0 }); } spawnTimer = setTimeout(sp, 1450 + Math.random() * 900); } spawnTimer = setTimeout(sp, 1450);"
        text = text.replace(old, new)
        text = text.replace("spd = 4 + score * 0.003;", "spd = Math.min(8.2, 3.6 + score * 0.0016);")
        text = text.replace(
            "function end() { cancelAnimationFrame(animId); running = false;",
            "function end() { cancelAnimationFrame(animId); if (spawnTimer) { clearTimeout(spawnTimer); spawnTimer = null; } running = false;"
        )
        # Root cause: the upstream page calls draw() while obstacles/dino are undefined.
        text = text.replace("        draw();\n    </script>", "        init(); draw();\n    </script>")

    elif slug == "44-car-racing":
        text = text.replace("speed = 3; frame = 0; running = true;", "speed = 2.8; frame = 0; running = true;")
        text = text.replace("speed = Math.min(12, speed + 0.5);", "speed = Math.min(8.5, speed + 0.35);")
        text = text.replace(
            "if (frame % Math.max(30, 80 - score / 100) === 0) spawnObstacle();",
            "if (frame % Math.max(52, 105 - Math.floor(score / 180)) === 0 && obstacles.length < 4) spawnObstacle();"
        )

    elif slug == "55-space-defender":
        text = text.replace(
            "if (frame % Math.max(20, 60 - wave * 3) === 0) spawnEnemy();",
            "if (frame % Math.max(38, 78 - wave * 2) === 0 && enemies.length < 9) spawnEnemy();"
        )
        text = text.replace("if (frame % 300 === 0 && running) { wave++;", "if (frame % 420 === 0 && running) { wave++;")

    elif slug == "56-zombie-shooter":
        text = text.replace("for (let i = 0; i < 4 + wave * 3; i++)", "for (let i = 0; i < Math.min(12, 3 + wave * 2); i++)")
        text = text.replace(
            "speed: 0.5 + Math.random() * 0.5 + wave * 0.1",
            "speed: 0.42 + Math.random() * 0.38 + Math.min(0.55, wave * 0.055)"
        )

    elif slug == "59-gravity-ball":
        text = text.replace(
            "for (let i = 0; i < 2 + level; i++) { obstacles.push({ x: 80 + Math.random() * (W - 160), y: 80 + Math.random() * (H - 160), r: 18 + Math.random() * 20 }); }",
            "for (let i = 0; i < Math.min(5, 1 + Math.floor(level / 2)); i++) { let x, y, tries = 0; do { x = 90 + Math.random() * (W - 180); y = 90 + Math.random() * (H - 180); tries++; } while (tries < 24 && (Math.hypot(x - ball.x, y - ball.y) < 110 || Math.hypot(x - target.x, y - target.y) < 105 || obstacles.some(o => Math.hypot(x - o.x, y - o.y) < o.r + 55))); obstacles.push({ x, y, r: 16 + Math.random() * 14 }); }"
        )

    return text

def cpu_patch(slug: str) -> str:
    patches = {
        "02-pong": """
(function(){
  var baseUpdate = update;
  update = function(){
    if (running) {
      var target = ball.vx < 0 ? ball.y - PAD_H/2 : H/2 - PAD_H/2;
      var speed = ball.vx < 0 ? 4.4 : 2.1;
      pl.y = Math.max(0, Math.min(H-PAD_H, pl.y + Math.max(-speed, Math.min(speed, target-pl.y))));
    }
    baseUpdate();
  };
  var msg=document.getElementById('msg'); if(msg) msg.textContent='Você controla a raquete da direita. Oponente: CPU.';
  window.__niaCpuReady=true;
})();""",
        "17-connect-four": """
(function(){
  var baseDrop=drop;
  function cols(b){var o=[];for(var c=0;c<COLS;c++)if(!b[0][c])o.push(c);return o;}
  function placed(b,col,p){var x=b.map(function(r){return r.slice();});for(var r=ROWS-1;r>=0;r--)if(!x[r][col]){x[r][col]=p;break;}return x;}
  function win(b,p){var d=[[0,1],[1,0],[1,1],[1,-1]];for(var r=0;r<ROWS;r++)for(var c=0;c<COLS;c++)if(b[r][c]===p)for(var q=0;q<d.length;q++){var ok=true;for(var k=1;k<4;k++){var rr=r+d[q][0]*k,cc=c+d[q][1]*k;if(rr<0||rr>=ROWS||cc<0||cc>=COLS||b[rr][cc]!==p){ok=false;break;}}if(ok)return true;}return false;}
  function choose(){var a=cols(board);for(var i=0;i<a.length;i++)if(win(placed(board,a[i],2),2))return a[i];for(var j=0;j<a.length;j++)if(win(placed(board,a[j],1),1))return a[j];var pref=[3,2,4,1,5,0,6];for(var p=0;p<pref.length;p++)if(a.indexOf(pref[p])>=0)return pref[p];return a[0];}
  function cpu(){if(gameOver||current!==2)return;var c=choose();if(c!==undefined)baseDrop(c);}
  drop=function(col){if(gameOver||current!==1)return;baseDrop(col);if(!gameOver&&current===2)setTimeout(cpu,280);};
  var a=document.getElementById('p1tag'),b=document.getElementById('p2tag');if(a)a.textContent='🔴 Você';if(b)b.textContent='🟡 CPU';window.__niaCpuReady=true;
})();""",
        "18-tic-tac-toe": """
(function(){setTimeout(function(){var b=document.getElementById('pvcBtn');if(b)b.click();var m=document.querySelector('.mode-btns');if(m)m.style.display='none';window.__niaCpuReady=true;},0);})();""",
        "19-checkers": """
(function(){
  var baseSelect=select;
  function list(){var jump=hasJumps(),o=[];for(var r=0;r<8;r++)for(var c=0;c<8;c++)if(isBlack(board[r][c]))getMoves(r,c,board).filter(function(m){return !jump||m.jump;}).forEach(function(m){o.push({r:r,c:c,m:m});});return o;}
  function cpu(){if(turn!==2)return;var a=list();if(!a.length)return;a.sort(function(x,y){return (y.m.jump?100:0)-(x.m.jump?100:0)+Math.random()-.5;});baseSelect(a[0].r,a[0].c);setTimeout(function(){baseSelect(a[0].m.r,a[0].m.c);},130);}
  select=function(r,c){if(turn!==1)return;var before=turn;baseSelect(r,c);if(before===1&&turn===2)setTimeout(cpu,260);};window.__niaCpuReady=true;
})();""",
        "20-chess": """
(function(){
  var baseClick=click, val={'♙':1,'♟':1,'♘':3,'♞':3,'♗':3,'♝':3,'♖':5,'♜':5,'♕':9,'♛':9,'♔':100,'♚':100};
  function cpu(){if(turn!=='black')return;var a=[];for(var i=0;i<64;i++)if(isBlack(board[i]))getMoves(i).forEach(function(to){a.push({from:i,to:to});});if(!a.length)return;a.sort(function(x,y){return (val[board[y.to]]||0)-(val[board[x.to]]||0)+Math.random()*.05;});baseClick(a[0].from);setTimeout(function(){baseClick(a[0].to);},140);}
  click=function(i){if(turn!=='white')return;var before=turn;baseClick(i);if(before==='white'&&turn==='black')setTimeout(cpu,300);};window.__niaCpuReady=true;
})();""",
        "35-dots-and-boxes": """
(function(){
  var busy=false;
  function lines(){var o=[];for(var r=0;r<=N;r++)for(var c=0;c<N;c++)if(hLines[r][c]<0)o.push({t:'h',r:r,c:c});for(var rr=0;rr<N;rr++)for(var cc=0;cc<=N;cc++)if(vLines[rr][cc]<0)o.push({t:'v',r:rr,c:cc});return o;}
  function play(l){var rect=c.getBoundingClientRect(),x=l.t==='h'?PAD+l.c*GAP+GAP/2:PAD+l.c*GAP,y=l.t==='h'?PAD+l.r*GAP:PAD+l.r*GAP+GAP/2;c.dispatchEvent(new MouseEvent('click',{bubbles:true,clientX:rect.left+x*(rect.width/c.width),clientY:rect.top+y*(rect.height/c.height)}));}
  function cpu(){if(turn!==1||busy)return;var a=lines();if(!a.length)return;busy=true;setTimeout(function(){play(a[Math.floor(Math.random()*a.length)]);busy=false;if(turn===1)setTimeout(cpu,180);},230);}
  c.addEventListener('click',function(){if(turn===1&&!busy)setTimeout(cpu,180);});window.__niaCpuReady=true;
})();""",
        "36-reversi": """
(function(){setTimeout(function(){var b=document.getElementById('aiMode');if(b)b.click();var m=document.querySelector('.mode-btns');if(m)m.style.display='none';window.__niaCpuReady=true;},0);})();""",
        "76-ludo": """
(function(){
  var d=document.getElementById('dice');if(d)d.addEventListener('click',function(e){if(turn===1){e.preventDefault();e.stopImmediatePropagation();}},true);
  setInterval(function(){if(tokens&&turn===1&&!rolled&&!tokens[1].done)rollDice();},650);window.__niaCpuReady=true;
})();""",
        "77-snakes-and-ladders": """
(function(){
  var b=document.getElementById('rollBtn');if(b)b.addEventListener('click',function(e){if(current===1){e.preventDefault();e.stopImmediatePropagation();}},true);
  setInterval(function(){if(players&&players.length>=2){players[0].name='VOCÊ';players[1].name='CPU';}if(current===1&&!rolling&&players&&players[1].pos<100)roll();},700);window.__niaCpuReady=true;
})();""",
    }
    return patches.get(slug, "")

def classify_input(text: str) -> tuple[str, str, str]:
    keyboard = bool(re.search(r"""keydown|keyup|keyCode|ArrowUp|ArrowDown|ArrowLeft|ArrowRight|event\.key|\.key\s*={2,3}""", text, re.I))
    profile = "KEYBOARD" if keyboard else "CURSOR"
    arrows = bool(re.search(r"""ArrowUp|ArrowDown|ArrowLeft|ArrowRight|keyCode\s*={2,3}\s*(37|38|39|40)""", text))
    wasd = bool(re.search(r"""KeyW|keyCode\s*={2,3}\s*(65|68|83|87)|["'][wasd]["']""", text))
    scheme = "WASD" if wasd and not arrows else "ARROWS"
    space = bool(re.search(r"""Space|keyCode\s*={2,3}\s*32|which\s*={2,3}\s*32|e\.key\s*===?\s*['"] ['"]""", text))
    enter = bool(re.search(r"""Enter|keyCode\s*={2,3}\s*13|which\s*={2,3}\s*13""", text))
    action = "Space" if space else ("Enter" if enter else "Enter")
    return profile, scheme, action

def layout_profile(slug: str, text: str, input_profile: str) -> dict:
    canvas = CANVAS_RE.search(text)
    if canvas:
        w, h = int(canvas.group(1)), int(canvas.group(2))
        ratio = w / max(1, h)
        orientation = "PORTRAIT" if ratio < 0.82 else ("LANDSCAPE" if ratio > 1.35 else "SQUARE")
        if orientation == "PORTRAIT":
            max_w, max_h = 58, 88
        elif orientation == "LANDSCAPE":
            max_w, max_h = 94, 76
        else:
            max_w, max_h = 84, 84
        if slug == "09-flappy-bird": max_w, max_h = 56, 88
        if slug == "10-dino-runner": max_w, max_h = 94, 68
        return {
            "type":"CANVAS","canvasWidth":w,"canvasHeight":h,"orientation":orientation,
            "maxWidthVw":max_w,"maxHeightVh":max_h,"cursorStep":48,
        }
    return {
        "type":"DOM","canvasWidth":0,"canvasHeight":0,"orientation":"RESPONSIVE",
        "maxWidthVw":94,"maxHeightVh":90,"cursorStep":54 if input_profile == "CURSOR" else 0,
    }

def layout_css(profile: dict) -> str:
    if profile["type"] == "CANVAS":
        return """
<style id="nia-layout-profile">
html,body{margin:0!important;width:100%!important;height:100%!important;overflow:hidden!important;background:#05070b!important}
body{min-height:100vh!important;justify-content:center!important;align-items:center!important}
canvas{width:auto!important;height:auto!important;max-width:%svw!important;max-height:%svh!important;object-fit:contain!important}
.back{display:none!important}
</style>
""" % (profile["maxWidthVw"], profile["maxHeightVh"])
    return """
<style id="nia-layout-profile">
html,body{margin:0!important;width:100%!important;min-height:100%!important;max-width:100vw!important;overflow:hidden!important}
.back{display:none!important}
</style>
"""

BRIDGE = r"""
__NIA_LAYOUT_CSS__
<style id="nia-tv-bridge-style">
.back,a[href="../../index.html"],a[href*="../index.html"]{display:none!important}
html,body{overscroll-behavior:none!important}
#__nia_cursor{position:fixed;left:50%;top:50%;width:24px;height:24px;margin:-12px 0 0 -12px;border:3px solid #38E8FF;border-radius:50%;box-shadow:0 0 0 2px rgba(9,11,16,.9),0 0 18px rgba(56,232,255,.8);z-index:2147483647;pointer-events:none;display:none;transition:left .04s linear,top .04s linear}
</style>
<div id="__nia_cursor" aria-hidden="true"></div>
<script id="nia-tv-bridge">
(function(){
"use strict";
var profile="__NIA_PROFILE__",cursorStep=__NIA_CURSOR_STEP__,cursor=document.getElementById("__nia_cursor");
var x=Math.max(20,innerWidth/2),y=Math.max(20,innerHeight/2),primaryUsed=false,mouseHeld=false;
var translations=__NIA_TRANSLATIONS__;
window.__niaLocale="pt-BR";
window.__niaGameReady=false;
window.__niaDifficulty="NORMAL";

function resetViewport(){try{scrollTo(0,0);document.documentElement.scrollTop=0;document.documentElement.scrollLeft=0;if(document.body){document.body.scrollTop=0;document.body.scrollLeft=0;}}catch(_){}}
window.__niaResetViewport=resetViewport;

window.__niaSetDifficulty=function(level){window.__niaDifficulty=level||"NORMAL";document.documentElement.dataset.niaDifficulty=window.__niaDifficulty;return window.__niaDifficulty;};

function visible(el){if(!el)return false;var r=el.getBoundingClientRect(),s=getComputedStyle(el);return r.width>0&&r.height>0&&s.display!=="none"&&s.visibility!=="hidden";}
function label(el){if(!el)return"";return String(el.innerText||el.textContent||el.value||el.getAttribute("aria-label")||"").trim().toLowerCase();}
function isPrimary(el){var v=label(el),a=["play","start","start game","new game","jogar","iniciar","iniciar jogo","iniciar quiz","começar","comecar","begin","go"];for(var i=0;i<a.length;i++)if(v===a[i]||v.indexOf(a[i]+" ")===0||v.indexOf(a[i]+":")===0)return true;return false;}
function primary(){var a=document.querySelectorAll("#startBtn,button,[role=button],input[type=button],input[type=submit],.btn");for(var i=0;i<a.length;i++)if(visible(a[i])&&isPrimary(a[i]))return a[i];return null;}

window.__niaActivatePrimary=function(){if(primaryUsed)return false;var p=primary();if(!p)return false;primaryUsed=true;try{p.click();resetViewport();document.documentElement.dataset.niaAutostart="done";return true;}catch(_){primaryUsed=false;return false;}};

function updateCursor(){if(cursor){cursor.style.left=x+"px";cursor.style.top=y+"px";}}
function candidateElements(){return Array.prototype.slice.call(document.querySelectorAll(".cell,.sq,.square,.card,.opt,.option,.choice-btn,.item,.dice,#rollBtn,button,[role=button],input,canvas")).filter(visible);}
function center(el){var r=el.getBoundingClientRect();return{x:r.left+r.width/2,y:r.top+r.height/2,el:el};}
function nearest(dx,dy){
  var a=candidateElements(),best=null,bestScore=Infinity;
  for(var i=0;i<a.length;i++){if(a[i]===cursor)continue;var p=center(a[i]),vx=p.x-x,vy=p.y-y;if(dx<0&&vx>=-4)continue;if(dx>0&&vx<=4)continue;if(dy<0&&vy>=-4)continue;if(dy>0&&vy<=4)continue;var primaryAxis=Math.abs(dx?vx:vy),cross=Math.abs(dx?vy:vx),score=primaryAxis+cross*2.2;if(score<bestScore){bestScore=score;best=p;}}
  return best;
}

window.__niaCursorMove=function(dx,dy){
  var under=document.elementFromPoint(x,y),canvas=under&&under.closest?under.closest("canvas"):null;
  if(!canvas){var n=nearest(dx,dy);if(n){x=n.x;y=n.y;updateCursor();return;}}
  x=Math.min(Math.max(16,x+Math.sign(dx)*cursorStep),Math.max(16,innerWidth-16));
  y=Math.min(Math.max(16,y+Math.sign(dy)*cursorStep),Math.max(16,innerHeight-16));
  updateCursor();
  if(mouseHeld){var t=document.elementFromPoint(x,y);if(t)t.dispatchEvent(new MouseEvent("mousemove",{bubbles:true,clientX:x,clientY:y,buttons:1}));}
};

window.__niaCursorDown=function(){
  var t=document.elementFromPoint(x,y);if(!t)return;mouseHeld=true;
  t.dispatchEvent(new MouseEvent("mousedown",{bubbles:true,clientX:x,clientY:y,button:0,buttons:1}));
};
window.__niaCursorUp=function(){
  var t=document.elementFromPoint(x,y);if(!t){mouseHeld=false;return;}
  t.dispatchEvent(new MouseEvent("mouseup",{bubbles:true,clientX:x,clientY:y,button:0,buttons:0}));
  if(mouseHeld){try{t.click();}catch(_){}}
  mouseHeld=false;
};
window.__niaCursorClick=function(){window.__niaCursorDown();setTimeout(window.__niaCursorUp,24);};

function replaceText(s){var out=String(s);Object.keys(translations).sort(function(a,b){return b.length-a.length;}).forEach(function(k){if(out.indexOf(k)>=0)out=out.split(k).join(translations[k]);});return out;}
function translateNode(root){
  try{
    var w=document.createTreeWalker(root,NodeFilter.SHOW_TEXT);var n,arr=[];while((n=w.nextNode()))arr.push(n);
    arr.forEach(function(t){var p=t.parentNode;if(!p||/^(SCRIPT|STYLE)$/i.test(p.nodeName))return;var v=replaceText(t.nodeValue);if(v!==t.nodeValue)t.nodeValue=v;});
    root.querySelectorAll&&root.querySelectorAll("input[placeholder],button[aria-label],[aria-label]").forEach(function(el){if(el.placeholder)el.placeholder=replaceText(el.placeholder);var a=el.getAttribute("aria-label");if(a)el.setAttribute("aria-label",replaceText(a));});
  }catch(_){}
}
window.__niaTranslate=function(){translateNode(document.body||document.documentElement);};

addEventListener("scroll",function(){requestAnimationFrame(resetViewport);},{passive:true});
addEventListener("load",function(){
  resetViewport();window.__niaTranslate();
  if(profile==="CURSOR"&&cursor){cursor.style.display="block";var a=candidateElements();if(a.length){var p=center(a[0]);x=p.x;y=p.y;}updateCursor();}
  setTimeout(function(){window.__niaActivatePrimary();window.__niaTranslate();resetViewport();},140);
  setTimeout(function(){window.__niaTranslate();document.documentElement.dataset.niaReady="1";window.__niaGameReady=true;},320);
});
try{new MutationObserver(function(){window.__niaTranslate();}).observe(document.documentElement,{childList:true,subtree:true,characterData:true});}catch(_){}
})();
</script>
"""

def inject_bridge(text: str, input_profile: str, slug: str, layout: dict) -> str:
    bridge = (
        BRIDGE
        .replace("__NIA_PROFILE__", input_profile)
        .replace("__NIA_CURSOR_STEP__", str(layout["cursorStep"] or 48))
        .replace("__NIA_TRANSLATIONS__", json.dumps(COMMON_PT, ensure_ascii=False))
        .replace("__NIA_LAYOUT_CSS__", layout_css(layout))
    )
    patch = cpu_patch(slug)
    if patch:
        bridge += "\n<script id=\"nia-cpu-opponent\">\n" + patch + "\n</script>\n"
    if re.search(r"</body\s*>", text, flags=re.I):
        return re.sub(r"</body\s*>", bridge + "\n</body>", text, count=1, flags=re.I)
    return text + "\n" + bridge

def main() -> int:
    if len(sys.argv) != 2:
        print("usage: import_games.py <upstream-checkout>", file=sys.stderr)
        return 2

    upstream = Path(sys.argv[1]).resolve()
    root = Path(__file__).resolve().parents[1]
    assets = root / "app" / "src" / "main" / "assets"
    games_out = assets / "games"
    licenses_out = assets / "licenses"

    if not (upstream / "LICENSE").exists() or not (upstream / "index.html").exists():
        raise SystemExit("Upstream checkout is incomplete.")

    license_text = (upstream / "LICENSE").read_text(encoding="utf-8")
    if "MIT License" not in license_text or "Copyright (c) 2026 Can" not in license_text:
        raise SystemExit("Upstream license identity did not match the pinned audited source.")

    index_text = (upstream / "index.html").read_text(encoding="utf-8")
    meta = {
        int(m.group(1)): {"name":html.unescape(m.group(2)),"icon":html.unescape(m.group(3)),"category":m.group(4).lower()}
        for m in GAME_ROW_RE.finditer(index_text)
    }
    sources = sorted((upstream / "games").glob("*/index.html"), key=lambda p:int(p.parent.name.split("-",1)[0]))
    if len(sources) != EXPECTED_GAMES or len(meta) != EXPECTED_GAMES:
        raise SystemExit("Upstream catalog count mismatch.")

    if games_out.exists():
        shutil.rmtree(games_out)
    games_out.mkdir(parents=True, exist_ok=True)
    licenses_out.mkdir(parents=True, exist_ok=True)

    catalog = []
    for source in sources:
        slug = source.parent.name
        game_id = int(slug.split("-",1)[0])
        item = meta[game_id]
        original = item["name"]
        title = TITLE_PT.get(game_id, original)

        text = source.read_text(encoding="utf-8")
        text = strip_remote_fonts(text)
        text = text.replace(original, title)
        text = localize_content(slug, text)
        text = apply_gameplay_patch(slug, text)
        text = translate_common(text)

        input_profile, scheme, action = classify_input(text)
        layout = layout_profile(slug, text, input_profile)
        text = inject_bridge(text, input_profile, slug, layout)

        if REMOTE_LINK_RE.search(text):
            raise SystemExit(slug + ": remote dependency remained")

        dest = games_out / slug
        dest.mkdir(parents=True, exist_ok=True)
        (dest / "index.html").write_text(text, encoding="utf-8", newline="\n")

        lowered = text.lower()
        has_cpu_native = bool(re.search(r"\b(cpu|computer|versus computer|vs computer|ai mode|pvc)\b", lowered))
        play_mode = "VS_CPU" if slug in CPU_PATCH_GAMES or has_cpu_native else "SOLO"
        controls = "Setas + OK" if input_profile == "KEYBOARD" else "Cursor NIA: setas + OK"
        if input_profile == "CURSOR":
            controls += " • segure OK para arrastar"

        catalog.append({
            "id": game_id,
            "slug": slug,
            "title": title,
            "originalTitle": original,
            "icon": item["icon"],
            "category": item["category"],
            "categoryPt": CATEGORY_PT.get(item["category"], item["category"]),
            "inputProfile": input_profile,
            "directionScheme": scheme,
            "actionKey": action,
            "controlsPt": controls,
            "playMode": play_mode,
            "autoStart": "startBtn" in text or "id=\"startBtn\"" in text,
            "supportsDifficulty": slug in DIFFICULTY_GAMES,
            "layoutType": layout["type"],
            "canvasWidth": layout["canvasWidth"],
            "canvasHeight": layout["canvasHeight"],
            "orientation": layout["orientation"],
            "maxWidthVw": layout["maxWidthVw"],
            "maxHeightVh": layout["maxHeightVh"],
            "cursorStep": layout["cursorStep"],
            "sourceUrl": UPSTREAM_REPO + "/tree/" + UPSTREAM_COMMIT + "/games/" + slug,
            "upstreamCommit": UPSTREAM_COMMIT,
            "license": "MIT",
            "locale": "pt-BR",
            "niaCertified": True,
        })

    (assets / "catalog.json").write_text(json.dumps(catalog, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    (licenses_out / "100htmlgameshub-MIT.txt").write_text(license_text, encoding="utf-8")

    notice = f"""# Avisos de terceiros

## 100 HTML Games Collection

- Projeto original: {UPSTREAM_REPO}
- Commit fixado: {UPSTREAM_COMMIT}
- Copyright: Copyright (c) 2026 Can
- Licença: MIT
- Alterações NIA: empacotamento offline, remoção de fontes remotas, tradução pt-BR,
  perfis individuais de TV, controles por controle remoto, cursor com arraste,
  ajustes de dificuldade, correções de inicialização e oponentes CPU em jogos locais selecionados.

O texto integral da licença MIT está em:
app/src/main/assets/licenses/100htmlgameshub-MIT.txt
"""
    (root / "THIRD_PARTY_NOTICES.md").write_text(notice, encoding="utf-8")

    print("Imported", len(catalog), "games")
    print("pt-BR:", sum(1 for g in catalog if g["locale"] == "pt-BR"))
    print("CPU:", sum(1 for g in catalog if g["playMode"] == "VS_CPU"))
    print("Difficulty profiles:", sum(1 for g in catalog if g["supportsDifficulty"]))
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
