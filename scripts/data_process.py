import json
import sys

def process_file(input_file, output_file):
    """Process a JSONL file containing Wikipedia article data and combine paragraphs.
    Each line in the input file should be a valid JSON object."""
    try:
        with open(input_file, 'r', encoding='utf-8') as infile, \
             open(output_file, 'a', encoding='utf-8') as outfile:
            
            for line in infile:
                if not line.strip():
                    continue
                    
                try:
                    data = json.loads(line)
                    paragraphs = []
                    
                    # Extract all paragraphs from all sections
                    for section in data.get('sections', []):
                        for part in section.get('has_parts', []):
                            if part.get('type') == 'paragraph':
                                paragraphs.append(part.get('value', ''))
                    
                    # Write each paragraph on its own line
                    if paragraphs:
                        outfile.write('\n'.join(paragraphs) + '\n\n')
                        
                except json.JSONDecodeError:
                    print(f"Error: Could not parse JSON from line", file=sys.stderr)
                    continue
                
    except FileNotFoundError:
        print(f"Error: Could not find input file '{input_file}'", file=sys.stderr)
        sys.exit(1)
    except PermissionError:
        print(f"Error: Permission denied accessing files", file=sys.stderr)
        sys.exit(1)

if __name__ == "__main__":
    if len(sys.argv) > 2:
        process_file(sys.argv[1], sys.argv[2])
    else:
        print("Usage: python data_process.py input_file.jsonl output_file.txt")