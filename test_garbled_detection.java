import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

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
        System.out.println("测试乱码检测功能");
        
        // 测试用例1：正常中文文本
        String normalChinese = "这是一首中文歌曲";
        System.out.println("\n测试用例1: " + normalChinese);
        System.out.println("检测结果: " + (containsObviousGarbledCharacters(normalChinese) ? "包含乱码" : "正常文本"));
        
        // 测试用例2：包含欧洲字符的文本（可能是Big5被误解析为UTF-8）
        String europeanChars = "Café résumé naïve";
        System.out.println("\n测试用例2: " + europeanChars);
        System.out.println("检测结果: " + (containsObviousGarbledCharacters(europeanChars) ? "包含乱码" : "正常文本"));
        
        // 测试用例3：包含Box Drawing字符的文本
        String boxDrawing = "歌曲：┌────────┐";
        System.out.println("\n测试用例3: " + boxDrawing);
        System.out.println("检测结果: " + (containsObviousGarbledCharacters(boxDrawing) ? "包含乱码" : "正常文本"));
        
        // 测试用例4：模拟Big5编码被误解析为UTF-8
        byte[] big5Bytes = {(byte)0xA7, (byte)0x41, (byte)0xA6, (byte)0x6E}; // "歌曲"的Big5编码
        String misinterpreted = new String(big5Bytes, StandardCharsets.UTF_8);
        System.out.println("\n测试用例4: " + misinterpreted);
        System.out.println("检测结果: " + (containsObviousGarbledCharacters(misinterpreted) ? "包含乱码" : "正常文本"));
        
        // 测试用例5：混合文本（部分正常，部分乱码）
        String mixedText = "正常文本§¶•†‡部分乱码";
        System.out.println("\n测试用例5: " + mixedText);
        System.out.println("检测结果: " + (containsObviousGarbledCharacters(mixedText) ? "包含乱码" : "正常文本"));
    }
}