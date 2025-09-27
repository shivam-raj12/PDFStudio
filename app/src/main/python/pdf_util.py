# File: app/src/main/python/pdf_processor.py

import pypdf
import os
import java # This import is crucial for isinstance and other Java interop

def merge_reorder_pdfs_to_temporary_file(output_temp_pdf_path, ordered_page_sources_from_kotlin):
    """
    Merges and reorders pages from specified PDF files into a new temporary PDF.

    Args:
        output_temp_pdf_path (str): The absolute path to save the merged temporary PDF.
        ordered_page_sources_from_kotlin (java.util.List or list):
            A list of dictionaries, where each dictionary contains:
            'filePath': Path to the original PDF file.
            'pageIndex': The 0-based index of the page in the original PDF.
            'rotation': (Optional) Degrees to rotate the page (e.g., 90, 180, 270).
                        Defaults to 0 if not provided.
    Returns:
        tuple: (bool, str) indicating (success, message_or_error_path)
               If successful, message_or_error_path is the output_temp_pdf_path.
               If failed, message_or_error_path is an error message.
    """
    print(f"Python: merge_reorder_pdfs_to_temporary_file called. Output to: {output_temp_pdf_path}")
    print(f"Python: Type of received page sources: {type(ordered_page_sources_from_kotlin)}")

    # --- THIS IS THE KEY FIX ---
    # Explicitly convert to a Python list if it's a Java List proxy
    if isinstance(ordered_page_sources_from_kotlin, java.util.List):
        print("Python: Detected java.util.List, converting to Python list.")
        ordered_page_sources_with_paths = list(ordered_page_sources_from_kotlin)
    else:
        # Assuming it's already a Python list or a compatible iterable
        ordered_page_sources_with_paths = ordered_page_sources_from_kotlin
    # --- END OF KEY FIX ---

    if not ordered_page_sources_with_paths: # Check if the list is empty after potential conversion
        print("Python Error: No page sources provided (list is empty).")
        return False, "Python Error: No page sources provided (list is empty)."

    print(f"Python: Processing {len(ordered_page_sources_with_paths)} page sources after conversion.") # Now len() should work

    merger = pypdf.PdfWriter() # Using PdfWriter for pypdf 4.x+ (PdfFileMerger is older)

    try:
        for i, page_source_info in enumerate(ordered_page_sources_with_paths):
            file_path = page_source_info.get('filePath')
            page_index = page_source_info.get('pageIndex')
            # rotation = page_source_info.get('rotation', 0) # Example for rotation

            if file_path is None or page_index is None:
                error_msg = f"Python Error: Missing 'filePath' or 'pageIndex' in item {i}."
                print(error_msg)
                return False, error_msg

            if not os.path.exists(file_path):
                error_msg = f"Python Error: File not found at '{file_path}' for item {i}."
                print(error_msg)
                return False, error_msg

            try:
                print(f"Python: Reading page {page_index} from {file_path}")
                reader = pypdf.PdfReader(file_path)
                if page_index >= len(reader.pages):
                    error_msg = f"Python Error: pageIndex {page_index} out of bounds for file {file_path} which has {len(reader.pages)} pages."
                    print(error_msg)
                    return False, error_msg

                # Add the specific page
                merger.add_page(reader.pages[page_index])

                # Example: If you wanted to apply rotation (ensure your Kotlin side sends 'rotation' if needed)
                # page_to_add = reader.pages[page_index]
                # if rotation != 0:
                #    page_to_add.rotate(rotation) # pypdf uses rotate() method
                # merger.add_page(page_to_add)

            except Exception as e:
                error_msg = f"Python Error: Failed to process page {page_index} from '{file_path}'. Error: {e}"
                print(error_msg)
                return False, error_msg

        with open(output_temp_pdf_path, "wb") as f_out:
            merger.write(f_out)
        merger.close() # Important to close the writer

        print(f"Python: Successfully merged PDF to {output_temp_pdf_path}")
        return True, output_temp_pdf_path

    except Exception as e:
        error_msg = f"Python Error: An unexpected error occurred during PDF merging. Error: {e}"
        print(error_msg)
        try:
            merger.close() # Attempt to close even on error
        except:
            pass # Ignore errors during close on error
        return False, error_msg

