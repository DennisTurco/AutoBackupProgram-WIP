package application;

import java.io.IOException;
import javax.swing.JFrame;

class MainAutoBackupProgram {

	public static void main(String[] args) throws IOException {
		FrameAutoBackup nb = new FrameAutoBackup();
		nb.setVisible(true);
		nb.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
	}
}