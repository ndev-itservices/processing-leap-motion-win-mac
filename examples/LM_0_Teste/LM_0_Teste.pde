/**
 * LM_0_Teste - verifica se o Leap Motion está a funcionar.
 *
 * Mostra o estado da ligação ao serviço Ultraleap, o dispositivo detectado,
 * o número de mãos e desenha as mãos. Se algo falhar, a mensagem no ecrã
 * diz o que falta (serviço parado, dispositivo desligado, ...).
 *
 * Leap Motion for Processing (fork ARM) - Tiago Martins
 */
import de.voidplus.leapmotion.*;

LeapMotion leap;

void setup() {
  size(800, 500);
  leap = new LeapMotion(this, true); // true = mensagens de diagnóstico na consola
  textFont(createFont("Helvetica", 14));
}

void draw() {
  background(30);

  // mãos
  for (Hand hand : leap.getHands()) {
    hand.draw();
  }

  // estado
  boolean ligado = leap.isConnected();
  fill(ligado ? color(80, 220, 120) : color(240, 90, 90));
  noStroke();
  ellipse(24, 24, 16, 16);
  fill(255);
  textAlign(LEFT, CENTER);
  if (ligado) {
    text("Leap Motion ligado  |  " + leap.getFrameRate() + " fps  |  mãos: " + leap.countHands()
      + "  |  dedos: " + leap.countFingers(), 42, 24);
  } else {
    text("Leap Motion NÃO ligado. Verificar: serviço Ultraleap a correr? dispositivo no USB?", 42, 24);
  }

  int y = 60;
  for (Device d : leap.getDevices()) {
    text("Dispositivo: " + d.getRaw() + "  (FOV " + nf(degrees(d.getHorizontalViewAngle()), 0, 1) + "° x "
      + nf(degrees(d.getVerticalViewAngle()), 0, 1) + "°, alcance " + nf(d.getRange(), 0, 0) + " mm)", 42, y);
    y += 22;
  }

  for (Hand hand : leap.getHands()) {
    PVector p = hand.getRawPosition();
    text((hand.isLeft() ? "Esquerda" : "Direita") + "  id " + hand.getId()
      + "  posição (" + nf(p.x, 0, 0) + ", " + nf(p.y, 0, 0) + ", " + nf(p.z, 0, 0) + ") mm"
      + "  agarrar " + nf(hand.getGrabStrength(), 0, 2) + "  pinça " + nf(hand.getPinchStrength(), 0, 2)
      + "  dedos esticados " + hand.getOutstretchedFingers().size(), 42, y);
    y += 22;
  }

  fill(160);
  text("Versão da biblioteca " + leap.getVersion() + "  -  " + leap.getVersionSDK(), 42, height - 20);
}

void leapOnConnect() {
  println("Leap Motion: dispositivo ligado");
}

void leapOnDisconnect() {
  println("Leap Motion: dispositivo desligado");
}
