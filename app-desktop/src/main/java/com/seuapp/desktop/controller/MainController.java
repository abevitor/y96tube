package com.seuapp.desktop.controller;

import com.seuapp.core.YtDlpService;
import javafx.fxml.FXML;

public class MainController {

    // TODO: campos @FXML correspondentes aos componentes do main-view.fxml
    // (ex: TextField urlField, ComboBox<VideoFormat> formatComboBox,
    // RadioButton mp3Radio/mp4Radio, ProgressBar progressBar, Label statusLabel, etc)

    // TODO: instância de YtDlpService (ex: via YtDlpService.withDefaultLocations(...))

    @FXML
    public void initialize() {
        // TODO: configurações iniciais da tela (ex: selecionar MP3 por padrão)
    }

    @FXML
    private void onListFormats() {
        // TODO: pegar o link digitado, chamar ytDlpService.listFormats(url)
        // em uma Task/Thread separada (pra não travar a interface),
        // e popular o ComboBox com o resultado
    }

    @FXML
    private void onDownload() {
        // TODO: pegar link + formato escolhido + tipo de saída (MP3/MP4),
        // escolher pasta de destino, chamar ytDlpService.download(...)
        // em uma Task/Thread separada, atualizando a ProgressBar via Platform.runLater
    }
}
