package application;

import java.awt.Color;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import javax.swing.ImageIcon;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileSystemView;

@SuppressWarnings("serial")
class AutoBackupProgram extends JFrame{
	static String current_file_opened;
	static String next_date_backup;
	static Integer days_interval_backup;
	static boolean auto_backup_option;
	
	private String info_fileString= "info.json";
	private String info_file_directoryString = ".//res//";
	private String saves_directoryString = ".//res//saves//";
	
	private static TimerAutoBackup thread_timer;
	private static JSONAutoBackup JSON;
	
	private static DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
	private static DateTimeFormatter formatter_last_backup = DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm:ss a");
	private static LocalDate date_now;
	
	public AutoBackupProgram() { 
		
		JSON = new JSONAutoBackup();
		
		//auto backup control
		autoBackupControl();
		
		//set text values
		setTextValues();
		
		
	}
	
	// JMenuItem function
	public void Clear() { 
		System.out.println("Event --> clear");
		FrameAutoBackup.start_path.setText("");
		FrameAutoBackup.destination_path.setText("");
		FrameAutoBackup.message.setText("");
		FrameAutoBackup.last_backup.setText("");
	}
	
	// JMenuItem function
	public void Exit() {
		System.out.println("Event --> exit");
		System.exit(EXIT_ON_CLOSE);
	}
	
	// JMenuItem function
	public void Help() {
		System.out.println("Event --> help");
		
		ImageIcon icon = new ImageIcon(".//res//info.png");
		JOptionPane.showMessageDialog(null, 
				"....for questions please contact the author: -> dennisturco.github.io",
				"Help",
				JOptionPane.PLAIN_MESSAGE, icon); //messaggio popup
	}
	
	// JMenuItem function
	public void Credits() {
		System.out.println("Event --> credits");
		ImageIcon icon = new ImageIcon(".//res//author_logo.png");
		JOptionPane.showMessageDialog(null, 
				"<html><u>2022 � Dennis Turco</u></html>\r\n"
				+ "<html><i>Author</i>: Dennis Turco</html>\r\n"
				+ "<html><i>GitHub</i>: <a href='https://github.com/DennisTurco'>https://github.com/DennisTurco </a></html>\r\n"
				+ "<html><i>Web Site</i>: <a href='https://dennisturco.github.io/'>https://dennisturco.github.io/ </a></html>",
				"Credits",
				JOptionPane.PLAIN_MESSAGE, icon); //messaggio popup
	}
	
	// JMenuItem function
	public void Share() {
		System.out.println("Event --> share");
		
		//messaggio pop-up
		JOptionPane.showMessageDialog(null, "Share link copied to clipboard!");
        
		//copio nella clipboard il link
        String testString = "https://github.com/DennisTurco/Minesweeper-Game"; //TODO: mettere il link corretto
        StringSelection stringSelectionObj = new StringSelection(testString);
        Clipboard clipboardObj = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboardObj.setContents(stringSelectionObj, null);
	}
	
	// JMenuItem function
	public void viewHistory() throws Exception {
		System.out.println("Event --> history");
		Runtime.getRuntime().exec("notepad.exe .//res//log_file");
	}
	
	// JMenuItem function
	public void NewFile() {
		
		// pulisco tutto
		Clear();
		
		// di base l'auto enable � disattivato
		auto_backup_option = false;
		changeBTNAutoBackupOption(auto_backup_option);
		
		// tolgo il file attuale aperto
		current_file_opened = null;
		
		FrameAutoBackup.setCurrentFileName(current_file_opened);
	}
	
	// JMenuItem function
	public void RemoveSingleFile() {
		System.out.println("Event --> remove file");
		
		// ottengo il nome del file selezionato
		String filename = getFile(".//res//saves"); 
		
		//elimino
		File file = new File(saves_directoryString + filename);
		if (file.delete()) System.out.println("Event --> file deleted: " + file.getName());
		else System.out.println("Failed to delete the file.");	
		
		JSON.LoadJSONBackupList(); //aggiorno lista backup
	}
	
	// JMenuItem function
	public void Open() {
		System.out.println("Event --> open");
		
		// ottengo il nome del file selezionato
		current_file_opened = getFile(".//res//saves"); 
		
		// leggo da file json
		JSON.ReadJSONFile(current_file_opened, saves_directoryString);
	}
	
	// JMenuItem function
	public void SaveWithName() {
		System.out.println("Event --> save with name");
		
		//file
		String file_name;
		do {
		    file_name = JOptionPane.showInputDialog(null, "Name of the file"); //messaggio popup
		} while (file_name.equals("null") ||  file_name.equals("null*"));	
		if (file_name.length() == 0 || file_name == null) return; //caso in cui l'utente non abbia inserito il nome (stringa vuota)
		
		current_file_opened = file_name;
		
		current_file_opened += ".json";
		
		JSON.WriteJSONFile(current_file_opened, saves_directoryString);
	}
	
	// JMenuItem function
	public void Save() { 
		System.out.println("Event --> save");
		
		if (current_file_opened == null) {
			SaveWithName();
		}
		
		File file = new File(saves_directoryString + current_file_opened); // controllo se il file esiste
		if(file.exists() && !file.isDirectory()) { 
			JSON.WriteJSONFile(current_file_opened, saves_directoryString);
		}
		
		JSON.LoadJSONBackupList(); //aggiorno lista backup
	}
	
	// JMenuItem function
	public void BackupList() {
		JSON.openBackupList();
	}
	
	// button function
	
	public void SingleBackup() {   
		System.out.println("Event --> single backup");
		
		String temp = "\\";
		
		//------------------------------INPUT CONTROL ERRORS------------------------------
		if(checkInputCorrect() == false) return;  //controllo errori tramite funzione
		
		//------------------------------TO GET THE CURRENT DATE------------------------------
		date_now = LocalDate.now();
		
		//------------------------------SET ALL THE VARIABLES------------------------------
		String path1 = FrameAutoBackup.start_path.getText();
		String path2 = FrameAutoBackup.destination_path.getText();
		String name1; //nome cartella/file iniziale
		String date = formatter.format(date_now);
		
		//------------------------------SET ALL THE STRINGS------------------------------
		name1 = path1.substring(path1.length()-1, path1.length()-1);
		
		for(int i=path1.length()-1; i>=0; i--) {
			if(path1.charAt(i) != temp.charAt(0)) name1 = path1.charAt(i) + name1;
			else break;
		}

		path2 = path2 + "\\" + name1 + " (Backup " + date + ")";
		
		
		//------------------------------COPY THE FILE OR DIRECTORY------------------------------
        System.out.println("date backup: " + date);
    	
        if (current_file_opened != null) { // se current_file_opened � null significa che non sono in un salvataggio ma � un backup senza json file associato quindi non salvo la stringa last_backup
        	setStringToText(); //chiamata alla funzione
        }
        
        try {
			copyDirectoryFileVisitor(path1, path2); // funzione per copiare   
		} catch (IOException e) {
			System.err.println("Exception --> " + e);
			e.printStackTrace();
		} 
		
        JOptionPane.showMessageDialog(null, "Files Copied!\nFrom: " + FrameAutoBackup.start_path.getText() + "\nTo: " + FrameAutoBackup.destination_path.getText(), "AutoBackupProgram", 1);
        FrameAutoBackup.message.setForeground(Color.GREEN);
        
        if (auto_backup_option == true) {
        	//aggiorno il next day backup
			next_date_backup = date_now.plusDays(days_interval_backup).format(formatter).toString();
        } 
        
        if (current_file_opened != null) { // se current_file_opened � null significa che non sono in un salvataggio ma � un backup senza json file associato
	        JSON.WriteJSONFile(info_fileString, info_file_directoryString);
	        JSON.WriteJSONFile(current_file_opened, saves_directoryString);
	        JSON.LoadJSONBackupList(); //aggiorno lista backup
        }
        
        //attivo il timer di n secondi
		thread_timer = new TimerAutoBackup();
		thread_timer.startTimer();
        FrameAutoBackup.message.setText("Files Copied!");
        FrameAutoBackup.message.setVisible(true);
    }

	// button function
	public void AutomaticBackup() {
		System.out.println("Event --> automatic backup");
		
		if (current_file_opened != null) JSON.ReadJSONFile(info_fileString, info_file_directoryString);
		
		if(checkInputCorrect() == false) return;  //controllo errori tramite funzione 
		
		if(auto_backup_option == false) {
			// se il file non e' stato salvato bisogna salvarlo prima di settare l'auto backup
			if (current_file_opened == null) SaveWithName();
			if (current_file_opened == null) return;
			
			// message
			days_interval_backup = Integer.parseInt(JOptionPane.showInputDialog(null, "Every how many days run the auto backup?")); //messaggio popup
			if (days_interval_backup == JOptionPane.CANCEL_OPTION) return;
			
			//set date for next backup
			date_now = LocalDate.now();
			next_date_backup = date_now.plusDays(days_interval_backup).format(formatter).toString();
			System.out.println("Event --> Next date backup setted to " + next_date_backup);
			
			JOptionPane.showMessageDialog(null, "Auto Backup has been activated\n\tFrom: " + FrameAutoBackup.start_path.getText() + "\n\tTo: " + FrameAutoBackup.destination_path.getText() + "\nIs setted every " + days_interval_backup + " days", "AutoBackupProgram", 1);
		}
		
		changeBTNAutoBackupOption();

		// salvo nel JSON
		JSON.WriteJSONFile(current_file_opened, saves_directoryString);
		JSON.LoadJSONBackupList(); //aggiorno lista backup
	}
	
	public void autoBackupControl() {
		File directory = new File(".//res//saves");
		File[] listOfFiles = directory.listFiles();
		
		date_now = LocalDate.now();
		
		for (int i=0; i<directory.list().length; i++) { //procedo l'iterazione quante volte sono i file nella directory
			JSON.ReadJSONFile(listOfFiles[i].getName(), saves_directoryString);
			
			if (next_date_backup != null && auto_backup_option == true) {
				LocalDate time_next = LocalDate.parse(next_date_backup);
				
				if (time_next.compareTo(date_now) <= 0) {
					SingleBackup(); //eseguo il backup
				}
			}
		}
		JSON.LoadJSONBackupList(); //aggiorno lista backup
	}
	
	private String getFile(String directory_path) {
		JFileChooser jfc = new JFileChooser(FileSystemView.getFileSystemView().getHomeDirectory());
		jfc.setDialogTitle("Choose a file to open: ");
		jfc.setCurrentDirectory(new java.io.File(directory_path));
		jfc.setFileSelectionMode(JFileChooser.FILES_ONLY);

		int returnValue = jfc.showSaveDialog(null);
		if (returnValue == JFileChooser.APPROVE_OPTION) {
			if (jfc.getSelectedFile().isFile()) {
				System.out.println("You selected the file: " + jfc.getSelectedFile());
				
				//	dal percorso assoluto ottengo il nome 
				int counter = 0;
				for (long i=jfc.getSelectedFile().toString().length()-1; i>=0; i--) {
					if (jfc.getSelectedFile().toString().charAt((int) i) != '\\') counter++;
					else break;
				}
				
				return jfc.getSelectedFile().toString().substring(jfc.getSelectedFile().toString().length()-counter);
			}
		}
		return null;
	}
	
	public void changeBTNAutoBackupOption() {
		if (FrameAutoBackup.btn_automatic_backup.getText().equals("Auto Backup (Enabled)")) {
			FrameAutoBackup.btn_automatic_backup.setText("Auto Backup (Disabled)");
			auto_backup_option = false;
			System.out.println("Event --> Auto Backup setted to Disabled");
		}
		else {
			FrameAutoBackup.btn_automatic_backup.setText("Auto Backup (Enabled)");
			auto_backup_option = true;
			System.out.println("Event --> Auto Backup setted to Enabled");
		}
	}
	
	public void changeBTNAutoBackupOption(boolean option) {
		if (option == true) {
			FrameAutoBackup.btn_automatic_backup.setText("Auto Backup (Enabled)");
			System.out.println("Event --> Auto Backup setted to Enabled");
		}
		else {
			FrameAutoBackup.btn_automatic_backup.setText("Auto Backup (Disabled)");
			System.out.println("Event --> Auto Backup setted to Disabled");
		}
	}
	
	public static boolean getAutoBackupOption(String option) {
		if (option.equals("Auto Backup (Enabled)")) return true;
		else return false;
	}
	
	public void setStringToText() {
		try {
			LocalDateTime date_now = LocalDateTime.now();
			String last_date = formatter_last_backup.format(date_now);
			FrameAutoBackup.last_backup.setText("last backup: " + last_date);
			
			JSON.WriteJSONFile(info_fileString, info_file_directoryString);
		} catch(Exception e) {
			System.err.println("Exception --> " + e);
			e.printStackTrace();
		}
	}
	
	public void setTextValues() {
		JSON.ReadJSONFile(info_fileString, info_file_directoryString);
		
		if(days_interval_backup != null) auto_backup_option = true;
		else auto_backup_option = false;
		
		changeBTNAutoBackupOption(auto_backup_option);
	}
	
	public boolean checkInputCorrect() {
		String temp = "\\";
		
		//check if inputs are null
		if(FrameAutoBackup.start_path.getText().length() == 0 || FrameAutoBackup.destination_path.getText().length() == 0) {
			// messsaggio errore
			setMessageError("Input Missing!");
			
			return false;
		}
		
		//check if there is a \ char
		boolean check1 = false;
		boolean check2 = false;
		for(int i=0; i<FrameAutoBackup.start_path.getText().length(); i++) {
			if(FrameAutoBackup.start_path.getText().charAt(i) == temp.charAt(0)) check1 = true;
		}
		
		for(int i=0; i<FrameAutoBackup.destination_path.getText().length(); i++) {
			if(FrameAutoBackup.destination_path.getText().charAt(i) == temp.charAt(0)) check2 = true;
		}
		
		if(check1 != true || check2 != true) {
			// messsaggio errore
			setMessageError("Input Error!");
			
			return false;
		}
		
		return true;
	}
	
	private void setMessageError(String error_type) {
		System.err.println("Error --> " + error_type);
		FrameAutoBackup.message.setForeground(Color.RED);
		FrameAutoBackup.message.setText(error_type);
		FrameAutoBackup.message.setVisible(true);
		
		//attivo il timer di n secondi
		thread_timer = new TimerAutoBackup();
		thread_timer.startTimer();
	}
	
    public void copyDirectoryFileVisitor(String source, String target) throws IOException {
		//TODO: conto il numero di file nella directory e sotto-directory
		int file_number = countFilesInDirectory(new File(source));
		//System.out.println(file_number);
		
		TreeCopyFileVisitor fileVisitor = new TreeCopyFileVisitor(source, target, file_number);
        Files.walkFileTree(Paths.get(source), fileVisitor);
    }
    
    public int countFilesInDirectory(File directory) {
    	int count = 0;
    	
    	for (File file : directory.listFiles()) {
    		if (file.isFile()) {
    			count++;
    		}
    	    	
	    	if (file.isDirectory()) {
	    		count += countFilesInDirectory(file);
	    	}
    	} 	
    	return count;
    }
    
    
    public void SelectionStart() {
		JFileChooser jfc = new JFileChooser(FileSystemView.getFileSystemView().getHomeDirectory());
		jfc.setDialogTitle("Choose a directory to save your file: ");
		jfc.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);

		int returnValue = jfc.showSaveDialog(null);
		if (returnValue == JFileChooser.APPROVE_OPTION) {
			if (jfc.getSelectedFile().isDirectory()) {
				System.out.println("You selected the directory: " + jfc.getSelectedFile());
				FrameAutoBackup.start_path.setText(jfc.getSelectedFile().toString());
			}
		}
	}
    
    public void SelectionDestination() {
		JFileChooser jfc = new JFileChooser(FileSystemView.getFileSystemView().getHomeDirectory());
		jfc.setDialogTitle("Choose a directory to save your file: ");
		jfc.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);

		int returnValue = jfc.showSaveDialog(null);
		if (returnValue == JFileChooser.APPROVE_OPTION) {
			if (jfc.getSelectedFile().isDirectory()) {
				System.out.println("You selected the directory: " + jfc.getSelectedFile());
				FrameAutoBackup.destination_path.setText(jfc.getSelectedFile().toString());
			}
		}
	}

}
