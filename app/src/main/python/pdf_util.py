from pypdf import PdfWriter, PdfReader
import os
import traceback

def merge_reorder_pdfs_to_temporary_file(output_temp_pdf_path, ordered_page_sources_with_paths):
    """
    Merges and reorders PDF pages based on the provided list and saves to a temporary path.

    Args:
        output_temp_pdf_path (str): The absolute path where the merged temporary PDF should be saved.
        ordered_page_sources_with_paths (list): A list of dictionaries, where each dictionary
            represents a page to be included in the output.
            Expected format: [{'file_path': '/path/to/temp_pdf1.pdf', 'original_page_index': 0}, ...]
                               file_path is the path to the temporary copy of the source PDF.

    Returns:
        tuple: (bool, str or None) -> (success_status, message_or_output_path)
               If successful, (True, output_temp_pdf_path).
               If failed, (False, error_message_string).
    """
    merger = PdfWriter()
    print(f"Python: Starting merge_reorder_pdfs_to_temporary_file. Output to: {output_temp_pdf_path}")
    print(f"Python: Received {len(ordered_page_sources_with_paths)} page sources.")

    if not ordered_page_sources_with_paths:
        return False, "Python Error: No page sources provided."

    try:
        for i, page_source_info in enumerate(ordered_page_sources_with_paths):
            pdf_file_path = page_source_info.get('file_path')
            # Ensure original_page_index is treated as int, handle potential None or non-int
            original_page_idx_raw = page_source_info.get('original_page_index')

            if pdf_file_path is None or original_page_idx_raw is None:
                return False, f"Python Error: Page source {i} is missing 'file_path' or 'original_page_index'."

            try:
                original_page_idx = int(original_page_idx_raw)
            except ValueError:
                return False, f"Python Error: 'original_page_index' for page source {i} is not a valid integer."


            print(f"Python: Processing source {i+1}: Path='{os.path.basename(pdf_file_path)}', Index={original_page_idx}")

            if not os.path.exists(pdf_file_path):
                return False, f"Python Error: Temporary input file not found: {pdf_file_path}"
            if os.path.getsize(pdf_file_path) == 0:
                return False, f"Python Error: Temporary input file is empty: {pdf_file_path}"


            try:
                reader = PdfReader(pdf_file_path)
                if 0 <= original_page_idx < len(reader.pages):
                    merger.add_page(reader.pages[original_page_idx])
                else:
                    return False, f"Python Error: Page index {original_page_idx} out of bounds for {os.path.basename(pdf_file_path)} (0-{len(reader.pages)-1})."
            except Exception as e_page:
                # Log the specific PDF and page that caused the error
                tb_str = traceback.format_exc()
                return False, f"Python Error processing page {original_page_idx} from {os.path.basename(pdf_file_path)}: {str(e_page)}\nTrace: {tb_str}"

        if not merger.pages: # Check if any pages were actually added to the output
            return False, "Python Error: No pages were successfully added to the output PDF. Input might be problematic."

        # Write the merged PDF to the temporary output path
        with open(output_temp_pdf_path, "wb") as f_out:
            merger.write(f_out)
        merger.close()
        print(f"Python: Successfully wrote merged PDF to {output_temp_pdf_path}")

        # Check if the output file was actually created and is not empty
        if os.path.exists(output_temp_pdf_path) and os.path.getsize(output_temp_pdf_path) > 0:
            return True, output_temp_pdf_path # Return the path of the successfully created temp output file
        else:
            # This case should ideally not be hit if merger.write() was successful
            # and no pages means the 'if not merger.pages' check above would trigger.
            # But good to have as a safeguard.
            return False, f"Python Error: Output file {output_temp_pdf_path} was not created or is empty after merging."

    except Exception as e_main:
        # Catch any other unexpected errors during the process
        tb_str = traceback.format_exc()
        return False, f"Python: An unexpected error occurred: {str(e_main)}\nTrace: {tb_str}"
