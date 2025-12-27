import java.nio.charset.StandardCharsets;

public class TestGarbledDetection {
    // 模拟ExoPlayerWrapper中的乱码检测方法
    private static boolean containsObviousGarbledCharacters(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        
        int garbledCharCount = 0;
        int totalCharCount = text.length();
        
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            
            // 检查是否是明显的乱码字符
            // 1. 检查半角片假名字符（通常是编码错误的标志）
            if (c >= 0xFF66 && c <= 0xFF9F) {
                garbledCharCount++;
                continue;
            }
            
            // 2. 检查欧洲扩展字符（可能是Big5被误解析为UTF-8的结果）
            if ((c >= 0x80 && c <= 0xFF) || 
                (c >= 0x100 && c <= 0x17F) || 
                (c >= 0x180 && c <= 0x24F)) {
                garbledCharCount++;
                continue;
            }
            
            // 3. 检查其他可能的乱码字符
            if ((c >= 0x2500 && c <= 0x257F) || // Box Drawing
                (c >= 0x2580 && c <= 0x259F) || // Block Elements
                (c >= 0x25A0 && c <= 0x25FF) || // Geometric Shapes
                (c >= 0x2600 && c <= 0x26FF) || // Miscellaneous Symbols
                (c >= 0x2700 && c <= 0x27BF)) { // Dingbats
                garbledCharCount++;
                continue;
            }
            
            // 4. 检查不常见的Unicode字符
            if (c >= 0x2000 && c <= 0x2FFF) {
                garbledCharCount++;
                continue;
            }
        }
        
        // 如果乱码字符比例超过20%，认为文本包含明显的乱码
        double garbledRatio = (double) garbledCharCount / totalCharCount;
        System.out.println("containsObviousGarbledCharacters - 乱码字符数: " + garbledCharCount + 
              "/" + totalCharCount + ", 比例: " + String.format("%.2f", garbledRatio));
        
        return garbledRatio > 0.2;
    }
    
    public static void main(String[] args) {
        System.out.println("Testing garbled character detection");
        
        // Test case 1: Normal text
        String normalText = "Normal text";
        System.out.println("\nTest case 1: " + normalText);
        System.out.println("Detection result: " + (containsObviousGarbledCharacters(normalText) ? "Contains garbled characters" : "Normal text"));
        
        // Test case 2: Text with European characters
        String europeanChars = "Cafe resume naive";
        System.out.println("\nTest case 2: " + europeanChars);
        System.out.println("Detection result: " + (containsObviousGarbledCharacters(europeanChars) ? "Contains garbled characters" : "Normal text"));
        
        // Test case 3: Text with Box Drawing characters
        String boxDrawing = "Song: \u2500\u2500\u2500\u2500\u2500\u2500";
        System.out.println("\nTest case 3: " + boxDrawing);
        System.out.println("Detection result: " + (containsObviousGarbledCharacters(boxDrawing) ? "Contains garbled characters" : "Normal text"));
        
        // Test case 4: Simulated Big5 encoding misinterpreted as UTF-8
        byte[] big5Bytes = {(byte)0xA7, (byte)0x41, (byte)0xA6, (byte)0x6E}; // "歌曲" in Big5
        String misinterpreted = new String(big5Bytes, StandardCharsets.UTF_8);
        System.out.println("\nTest case 4: " + misinterpreted);
        System.out.println("Detection result: " + (containsObviousGarbledCharacters(misinterpreted) ? "Contains garbled characters" : "Normal text"));
        
        // Test case 5: Mixed text (part normal, part garbled)
        String mixedText = "Normal text \u00A7\u00B6\u2022\u2020 part garbled";
        System.out.println("\nTest case 5: " + mixedText);
        System.out.println("Detection result: " + (containsObviousGarbledCharacters(mixedText) ? "Contains garbled characters" : "Normal text"));
    }
}