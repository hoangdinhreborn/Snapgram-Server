import os
import re
from pptx import Presentation

EMOJI_PATTERN = re.compile(
    r"[\U00010000-\U0010FFFF"
    r"\u2600-\u26FF\u2700-\u27BF"
    r"\u2300-\u23FF\u2B50\u2B55"
    r"\u200D\uFE0F]"
)

BANNED_CHARS = ["\u2013", "\u2014"] # en-dash and em-dash

FORBIDDEN_KEYWORDS_1_TO_12 = [
    "snapgram",
    "recommender",
    "kafka",
    "redis",
    "bài viết",
    "mạng xã hội",
]

def validate():
    pptx_path = "slides/snapgram-spring-batch.pptx"
    assert os.path.exists(pptx_path), f"File {pptx_path} does not exist!"
    
    prs = Presentation(pptx_path)
    total_slides = len(prs.slides)
    print(f"[*] Total slides count: {total_slides}")
    assert total_slides == 16, f"Expected 16 slides, got {total_slides}"
    
    errors = []
    
    for s_idx, slide in enumerate(prs.slides):
        slide_num = s_idx + 1
        slide_text = []
        
        # Collect all text in shapes
        for shape in slide.shapes:
            if shape.has_text_frame:
                for p in shape.text_frame.paragraphs:
                    slide_text.append(p.text)
                    
        full_slide_text = "\n".join(slide_text)
        
        # Collect notes text
        notes_text = ""
        if slide.has_notes_slide and slide.notes_slide.notes_text_frame:
            notes_text = slide.notes_slide.notes_text_frame.text
            
        # Check notes word count (40 - 80 words)
        words = [w for w in notes_text.split() if w.strip()]
        word_count = len(words)
        print(f"Slide {slide_num:02d}: Notes word count = {word_count}")
        if not (40 <= word_count <= 80):
            errors.append(f"Slide {slide_num}: Notes word count {word_count} is out of range [40, 80]")
            
        combined_text = full_slide_text + "\n" + notes_text
        
        # Check Banned Characters (en-dash, em-dash)
        for bc in BANNED_CHARS:
            if bc in combined_text:
                char_name = "EN DASH (U+2013)" if bc == "\u2013" else "EM DASH (U+2014)"
                errors.append(f"Slide {slide_num}: Contains banned character {char_name}")
                
        # Check Emojis
        emojis_found = EMOJI_PATTERN.findall(combined_text)
        if emojis_found:
            errors.append(f"Slide {slide_num}: Contains emojis: {emojis_found}")
            
        # Check Forbidden Keywords for slides 1 to 12
        if slide_num <= 12:
            lower_slide = full_slide_text.lower()
            lower_notes = notes_text.lower()
            for kw in FORBIDDEN_KEYWORDS_1_TO_12:
                if kw in lower_slide or kw in lower_notes:
                    errors.append(f"Slide {slide_num} (1-12): Contains forbidden keyword '{kw}'")
                    
    # PDF Font Embedding Check
    pdf_path = "slides/snapgram-spring-batch.pdf"
    assert os.path.exists(pdf_path), f"File {pdf_path} does not exist!"
    with open(pdf_path, 'rb') as f:
        pdf_raw = f.read().decode('latin1', errors='ignore')
        
    base_fonts = re.findall(r'/BaseFont\s*/([A-Za-z0-9+_-]+)', pdf_raw)
    font_files = re.findall(r'/FontFile[23]?\s+(\d+\s+\d+\s+R)', pdf_raw)
    print(f"[*] PDF BaseFonts: {set(base_fonts)}")
    print(f"[*] PDF Embedded FontFiles count: {len(font_files)}")
    if len(font_files) == 0:
        errors.append("PDF has NO embedded FontFiles!")
        
    print("\n" + "="*50)
    if errors:
        print(f"[!] Validation FAILED with {len(errors)} error(s):")
        for err in errors:
            print(f"  - {err}")
        return False
    else:
        print("[V] ALL VALIDATION CHECKS PASSED PERFECTLY!")
        return True

if __name__ == "__main__":
    success = validate()
    exit(0 if success else 1)
